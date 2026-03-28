package mera.mera_v2.entity;

public enum OrderStatus {
    NEW(0),
    PENDING(1),
    SHIPPING(6),
    COMPLETED(3),
    CANCELLED(5);

    private final int value;

    OrderStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OrderStatus fromValue(int value) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }
}