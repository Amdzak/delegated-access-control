package org.dac;

import com.owlike.genson.Genson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

@Contract(
        name = "DelegationContract",
        info = @Info(version = "1.0.0", title = "Delegation Contract", description = "Delegation Contract"))
@Default
public final class DelegationContract implements ContractInterface {

    private final Genson genson = new Genson();

    private enum DelegationErrors {
        DELEGATION_NOT_FOUND,
        DELEGATION_ALREADY_EXISTS,
        INVALID_PERMISSION,
        INVALID_TIME_RANGE,
        INVALID_DELEGATEE,
        NOT_ACTIVE,
        SAME_USER
    }

    // Create Delegation
    @Transaction
    public Delegation createDelegation(
            final Context ctx,
            final String delegationId,
            final String delegatorId,
            final String delegateeId,
            final String resourceId,
            final String permission,
            final String validFrom,
            final String validUntil,
            final int maxDepth) {
        ChaincodeStub stub = ctx.getStub();

        // Validation exists delegation
        if (delegationExists(ctx, delegationId)) {
            throw new ChaincodeException(
                    "Delegation " + delegationId + " already exists",
                    DelegationErrors.DELEGATION_ALREADY_EXISTS.toString());
        }

        // Validation Same user
        if (delegatorId.equals(delegateeId)) {
            throw new ChaincodeException(
                    "Delegator dan delegatee tidak boleh sama", DelegationErrors.SAME_USER.toString());
        }

        // Validation Invalid permission
        if (!isValidPermission(permission)) {
            throw new ChaincodeException(
                    "Permission tidak valid: " + permission, DelegationErrors.INVALID_PERMISSION.toString());
        }

        // Validation Time
        Instant from = Instant.parse(validFrom);
        Instant until = Instant.parse(validUntil);
        if (!until.isAfter(from)) {
            throw new ChaincodeException(
                    "validUntil harus lebih besar dari validFrom", DelegationErrors.INVALID_TIME_RANGE.toString());
        }

        // Create Time Now
        String createdAt = stub.getTxTimestamp().toString();

        // Bikin objek Delegation baru
        Delegation delegation = new Delegation(
                delegationId,
                delegatorId,
                delegateeId,
                resourceId,
                permission,
                validFrom,
                validUntil,
                maxDepth,
                Delegation.Status.ACTIVE.name(),
                createdAt,
                0 // root delegation
                );

        // Simpan ke ledger
        stub.putStringState(delegationId, genson.serialize(delegation));

        // Emit event untuk audit
        stub.setEvent("DelegationCreated", genson.serialize(delegation).getBytes());

        return delegation;
    }

    // Read Delegation
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Delegation readDelegation(final Context ctx, final String delegationId) {
        ChaincodeStub stub = ctx.getStub();
        String data = stub.getStringState(delegationId);

        if (data == null || data.isEmpty()) {
            throw new ChaincodeException(
                    "Delegation " + delegationId + " tidak ditemukan",
                    DelegationErrors.DELEGATION_NOT_FOUND.toString());
        }

        return genson.deserialize(data, Delegation.class);
    }

    // Revoke Delegation
    @Transaction
    public Delegation revokeDelegation(final Context ctx, final String delegationId) {
        ChaincodeStub stub = ctx.getStub();

        Delegation delegation = readDelegation(ctx, delegationId);

        if (!delegation.getStatus().equals(Delegation.Status.ACTIVE.name())) {
            throw new ChaincodeException(
                    "Hanya delegasi ACTIVE yang bisa di-revoke. Status: " + delegation.getStatus(),
                    DelegationErrors.NOT_ACTIVE.toString());
        }

        Delegation revoked = delegation.withStatus(Delegation.Status.REVOKED.name());

        stub.putStringState(delegationId, genson.serialize(revoked));
        stub.setEvent("DelegationRevoked", genson.serialize(revoked).getBytes());

        return revoked;
    }

    // Check Access
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String checkAccess(
            final Context ctx,
            final String delegationId,
            final String requesterDelegateeId,
            final String resourceId,
            final String permission) {

        Delegation d;
        try {
            d = readDelegation(ctx, delegationId);
        } catch (ChaincodeException e) {
            return "DENIED: delegasi tidak ditemukan";
        }

        // Cek 1: delegatee cocok
        if (!d.getDelegateeId().equals(requesterDelegateeId)) {
            return "DENIED: delegatee tidak cocok";
        }

        // Cek 2: status ACTIVE
        if (!d.getStatus().equals(Delegation.Status.ACTIVE.name())) {
            return "DENIED: status " + d.getStatus();
        }

        // Cek 3: resource cocok
        if (!d.getResourceId().equals(resourceId)) {
            return "DENIED: resource tidak cocok";
        }

        // Cek 4: permission cocok
        if (!d.getPermission().equals(permission)) {
            return "DENIED: permission tidak cocok";
        }

        // Cek 5: waktu
        Instant now = ctx.getStub().getTxTimestamp();
        Instant from = Instant.parse(d.getValidFrom());
        Instant until = Instant.parse(d.getValidUntil());

        if (now.isBefore(from)) {
            return "DENIED: belum berlaku";
        }
        if (now.isAfter(until)) {
            return "DENIED: expired";
        }

        return "GRANTED";
    }

    // Query By delegatee
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getDelegationsByDelegatee(final Context ctx, final String delegateeId) {
        ChaincodeStub stub = ctx.getStub();
        List<Delegation> result = new ArrayList<>();

        QueryResultsIterator<KeyValue> all = stub.getStateByRange("", "");
        for (KeyValue kv : all) {
            try {
                Delegation d = genson.deserialize(kv.getStringValue(), Delegation.class);
                if (d.getDelegateeId().equals(delegateeId)) {
                    result.add(d);
                }
            } catch (Exception ignored) {
                // skip kalau bukan Delegation
            }
        }
        return genson.serialize(result);
    }

    // Audit Trails
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getDelegationHistory(final Context ctx, final String delegationId) {
        ChaincodeStub stub = ctx.getStub();
        List<String> history = new ArrayList<>();

        QueryResultsIterator<KeyModification> iter = stub.getHistoryForKey(delegationId);

        for (KeyModification mod : iter) {
            history.add("txId=" + mod.getTxId()
                    + "|timestamp=" + mod.getTimestamp()
                    + "|isDelete=" + mod.isDeleted()
                    + "|value=" + mod.getStringValue());
        }
        return genson.serialize(history);
    }

    // Helper methods
    private boolean delegationExists(final Context ctx, final String delegationId) {
        String data = ctx.getStub().getStringState(delegationId);
        return data != null && !data.isEmpty();
    }

    private boolean isValidPermission(final String permission) {
        if (permission == null) {
            return false;
        }
        return permission.equals("READ") || permission.equals("WRITE") || permission.equals("READ_WRITE");
    }
}
