/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.dac;

import com.owlike.genson.annotation.JsonProperty;
import java.util.Objects;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType()
public final class Delegation {

    public enum Status {
        ACTIVE,
        REVOKED,
        EXPIRED
    }

    @Property()
    private final String delegationId;

    @Property()
    private final String parentDelegationId;

    @Property()
    private final String delegatorId;

    @Property()
    private final String delegateeId;

    @Property()
    private final String resourceId;

    @Property()
    private final String status;

    @Property()
    private final String permission;

    @Property()
    private final String validFrom;

    @Property()
    private final String validUntil;

    @Property()
    private final int maxDepth;

    @Property()
    private final String createdAt;

    @Property()
    private final int depth;

    public Delegation(
            @JsonProperty("delegationId") final String delegationId,
            @JsonProperty("parentDelegationId") final String parentDelegationId,
            @JsonProperty("delegatorId") final String delegatorId,
            @JsonProperty("delegateeId") final String delegateeId,
            @JsonProperty("resourceId") final String resourceId,
            @JsonProperty("permission") final String permission,
            @JsonProperty("validFrom") final String validFrom,
            @JsonProperty("validUntil") final String validUntil,
            @JsonProperty("maxDepth") final int maxDepth,
            @JsonProperty("status") final String status,
            @JsonProperty("createdAt") final String createdAt,
            @JsonProperty("depth") final int depth) {
        this.delegationId = delegationId;
        this.parentDelegationId = parentDelegationId != null ? parentDelegationId : "";
        this.delegatorId = delegatorId;
        this.delegateeId = delegateeId;
        this.resourceId = resourceId;
        this.permission = permission;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.maxDepth = maxDepth;
        this.status = status;
        this.createdAt = createdAt;
        this.depth = depth;
    }

    // GETTER
    public String getDelegationId() {
        return delegationId;
    }

    public String getParentDelegationId() {
        return parentDelegationId != null ? parentDelegationId : "";
    }

    public String getDelegatorId() {
        return delegatorId;
    }

    public String getDelegateeId() {
        return delegateeId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPermission() {
        return permission;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public String getValidUntil() {
        return validUntil;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public int getDepth() {
        return depth;
    }

    public Delegation withStatus(final String newStatus) {
        return new Delegation(
                delegationId,
                parentDelegationId,
                delegatorId,
                delegateeId,
                resourceId,
                permission,
                validFrom,
                validUntil,
                maxDepth,
                newStatus,
                createdAt,
                depth);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        Delegation other = (Delegation) obj;

        return Objects.deepEquals(
                        new String[] {
                            getDelegationId(),
                            getParentDelegationId(),
                            getDelegatorId(),
                            getDelegateeId(),
                            getResourceId(),
                            getPermission(),
                            getValidFrom(),
                            getValidUntil(),
                            getCreatedAt()
                        },
                        new String[] {
                            other.getDelegationId(),
                            other.getParentDelegationId(),
                            other.getDelegatorId(),
                            other.getDelegateeId(),
                            other.getResourceId(),
                            other.getPermission(),
                            other.getValidFrom(),
                            other.getValidUntil(),
                            other.getCreatedAt()
                        })
                && Objects.deepEquals(
                        new int[] {getMaxDepth(), getDepth()}, new int[] {other.getMaxDepth(), other.getDepth()});
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getDelegationId(),
                getParentDelegationId(),
                getDelegatorId(),
                getDelegateeId(),
                getResourceId(),
                getPermission(),
                getValidFrom(),
                getValidUntil(),
                getMaxDepth(),
                getCreatedAt(),
                getDepth());
    }

    @Override
    public String toString() {
        return "Delegation{id='" + delegationId + "', parentId='" + parentDelegationId + "', status='" + status
                + "', delegatee='" + delegateeId + "', depth=" + depth + "}";
    }
}
