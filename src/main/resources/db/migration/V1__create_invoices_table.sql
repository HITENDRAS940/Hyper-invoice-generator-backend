-- Flyway Migration: V1 - Create invoices table
-- Matches Invoice entity: id, invoice_number, booking_id, customer_name,
--                         customer_email, amount, invoice_date, cloudinary_url, created_at

CREATE TABLE IF NOT EXISTS invoices
(
    id              BIGSERIAL       PRIMARY KEY,
    invoice_number  VARCHAR(255)    NOT NULL UNIQUE,
    booking_id      BIGINT,
    customer_name   VARCHAR(255)    NOT NULL,
    customer_email  VARCHAR(255)    NOT NULL,
    amount          NUMERIC(12, 2)  NOT NULL,
    invoice_date    DATE,
    cloudinary_url  VARCHAR(1000),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index for fast lookups by invoice number
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_number ON invoices (invoice_number);

-- Index for lookups by booking
CREATE INDEX IF NOT EXISTS idx_invoices_booking_id ON invoices (booking_id);

