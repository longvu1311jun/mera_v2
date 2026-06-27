-- Create sequences for batch insert optimization
-- Replaces IDENTITY generator to enable Hibernate batch insert

CREATE SEQUENCE IF NOT EXISTS order_status_history_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS order_payment_seq START WITH 1 INCREMENT BY 50;
