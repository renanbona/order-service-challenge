INSERT INTO products (sku, name, price, stock) VALUES ('NB-001', 'Notebook Pro 14', 4500.00, 10);
INSERT INTO products (sku, name, price, stock) VALUES ('MS-002', 'Wireless Mouse', 150.00, 50);
INSERT INTO products (sku, name, price, stock) VALUES ('KB-003', 'Mechanical Keyboard', 350.00, 2);

INSERT INTO coupons (code, percent_off, expires_at) VALUES ('WELCOME10', 10, DATE '2099-12-31');
INSERT INTO coupons (code, percent_off, expires_at) VALUES ('LASTDAY15', 15, CURRENT_DATE);
INSERT INTO coupons (code, percent_off, expires_at) VALUES ('EXPIRED20', 20, DATEADD('DAY', -1, CURRENT_DATE));
INSERT INTO coupons (code, percent_off, expires_at, product_id) VALUES ('MOUSE10', 10, DATE '2099-12-31', 2);
INSERT INTO coupons (code, percent_off, expires_at, product_id) VALUES ('NOTEBOOK5OLD', 5, DATEADD('DAY', -1, CURRENT_DATE), 1);
