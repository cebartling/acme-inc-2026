-- Seed sample products for development and testing
-- 30 PUBLISHED + 3 ARCHIVED = 33 total

-- Widget products (11 items)
INSERT INTO products (id, slug, name, description, price, status, category, tags) VALUES
(gen_random_uuid(), 'premium-widget', 'Premium Widget', 'A high-quality premium widget for everyday use. This product features durable construction and sleek design.', 49.99, 'PUBLISHED', 'Electronics', 'widget,premium,electronics'),
(gen_random_uuid(), 'widget-pro-max', 'Widget Pro Max', 'The ultimate widget experience with pro-level features and maximum performance.', 129.99, 'PUBLISHED', 'Electronics', 'widget,pro,max,electronics'),
(gen_random_uuid(), 'basic-widget', 'Basic Widget', 'An affordable widget product perfect for beginners. Simple and reliable.', 9.99, 'PUBLISHED', 'Electronics', 'widget,basic,budget'),
(gen_random_uuid(), 'widget-deluxe', 'Widget Deluxe', 'Deluxe edition widget with enhanced product features and premium materials.', 79.99, 'PUBLISHED', 'Electronics', 'widget,deluxe,premium'),
(gen_random_uuid(), 'mini-widget', 'Mini Widget', 'Compact widget product that fits anywhere. Small size, big performance.', 19.99, 'PUBLISHED', 'Electronics', 'widget,mini,compact'),
(gen_random_uuid(), 'smart-widget', 'Smart Widget', 'IoT-enabled smart widget product with app connectivity and automation.', 89.99, 'PUBLISHED', 'Electronics', 'widget,smart,iot'),
(gen_random_uuid(), 'eco-widget', 'Eco Widget', 'Environmentally friendly widget product made from recycled materials.', 34.99, 'PUBLISHED', 'Home', 'widget,eco,green'),
(gen_random_uuid(), 'widget-ultra', 'Widget Ultra', 'Ultra-thin widget product with cutting-edge technology and sleek profile.', 149.99, 'PUBLISHED', 'Electronics', 'widget,ultra,thin'),
(gen_random_uuid(), 'widget-starter-kit', 'Widget Starter Kit', 'Complete starter kit with widgets and accessories. Everything you need to get started with widget products.', 59.99, 'PUBLISHED', 'Electronics', 'widget,kit,starter'),
(gen_random_uuid(), 'industrial-widget', 'Industrial Widget', 'Heavy-duty industrial widget product built for demanding environments.', 199.99, 'PUBLISHED', 'Tools', 'widget,industrial,heavy-duty'),
(gen_random_uuid(), 'wireless-widget', 'Wireless Widget', 'Cable-free wireless widget product with long battery life.', 69.99, 'PUBLISHED', 'Electronics', 'widget,wireless,portable');

-- Gadget products (6 items)
INSERT INTO products (id, slug, name, description, price, status, category, tags) VALUES
(gen_random_uuid(), 'super-gadget', 'Super Gadget', 'A versatile super gadget product for tech enthusiasts. Multi-functional design.', 45.99, 'PUBLISHED', 'Electronics', 'gadget,super,tech'),
(gen_random_uuid(), 'gadget-x', 'Gadget X', 'Next-generation gadget product with groundbreaking features and innovative design.', 159.99, 'PUBLISHED', 'Electronics', 'gadget,next-gen,innovative'),
(gen_random_uuid(), 'pocket-gadget', 'Pocket Gadget', 'Portable pocket-sized gadget product. Take this handy product anywhere you go.', 24.99, 'PUBLISHED', 'Electronics', 'gadget,pocket,portable'),
(gen_random_uuid(), 'gadget-pro', 'Gadget Pro', 'Professional-grade gadget product with advanced capabilities and robust build.', 119.99, 'PUBLISHED', 'Tools', 'gadget,pro,professional'),
(gen_random_uuid(), 'home-gadget', 'Home Gadget', 'Essential home gadget product that simplifies daily tasks around the house.', 39.99, 'PUBLISHED', 'Home', 'gadget,home,essential'),
(gen_random_uuid(), 'travel-gadget', 'Travel Gadget', 'Must-have travel gadget product. Compact, lightweight, and versatile for any trip.', 29.99, 'PUBLISHED', 'Electronics', 'gadget,travel,compact');

-- Product-themed items (13 items - ensures "product" search yields >24 results with widgets/gadgets)
INSERT INTO products (id, slug, name, description, price, status, category, tags) VALUES
(gen_random_uuid(), 'office-organizer', 'Office Organizer', 'A must-have product for keeping your office neat and organized.', 22.99, 'PUBLISHED', 'Office', 'organizer,office,product'),
(gen_random_uuid(), 'desk-lamp-pro', 'Desk Lamp Pro', 'Premium desk lamp product with adjustable brightness and color temperature.', 54.99, 'PUBLISHED', 'Office', 'lamp,desk,product'),
(gen_random_uuid(), 'ergonomic-mouse', 'Ergonomic Mouse', 'Comfortable ergonomic mouse product designed to reduce wrist strain.', 42.99, 'PUBLISHED', 'Electronics', 'mouse,ergonomic,product'),
(gen_random_uuid(), 'standing-desk-mat', 'Standing Desk Mat', 'Anti-fatigue standing desk mat product for all-day comfort.', 64.99, 'PUBLISHED', 'Office', 'mat,desk,product'),
(gen_random_uuid(), 'cable-management-kit', 'Cable Management Kit', 'Complete cable management product to declutter your workspace.', 15.99, 'PUBLISHED', 'Office', 'cable,management,product'),
(gen_random_uuid(), 'monitor-stand', 'Monitor Stand', 'Adjustable monitor stand product with built-in USB hub.', 74.99, 'PUBLISHED', 'Office', 'monitor,stand,product'),
(gen_random_uuid(), 'tool-set-premium', 'Tool Set Premium', 'Professional tool set product with 120 pieces in a durable case.', 149.99, 'PUBLISHED', 'Tools', 'tools,set,product'),
(gen_random_uuid(), 'air-purifier-compact', 'Air Purifier Compact', 'Compact air purifier product for small rooms and offices.', 89.99, 'PUBLISHED', 'Home', 'air,purifier,product'),
(gen_random_uuid(), 'noise-canceling-headphones', 'Noise Canceling Headphones', 'Premium noise canceling headphones product with superior sound quality.', 199.99, 'PUBLISHED', 'Electronics', 'headphones,audio,product'),
(gen_random_uuid(), 'smart-thermostat', 'Smart Thermostat', 'Energy-saving smart thermostat product with learning capabilities.', 129.99, 'PUBLISHED', 'Home', 'thermostat,smart,product'),
(gen_random_uuid(), 'portable-charger', 'Portable Charger', 'High-capacity portable charger product for all your devices.', 35.99, 'PUBLISHED', 'Electronics', 'charger,portable,product'),
(gen_random_uuid(), 'webcam-hd', 'Webcam HD', 'Full HD webcam product with built-in microphone and privacy shutter.', 59.99, 'PUBLISHED', 'Electronics', 'webcam,video,product'),
(gen_random_uuid(), 'keyboard-mechanical', 'Mechanical Keyboard', 'Premium mechanical keyboard product with customizable RGB lighting.', 109.99, 'PUBLISHED', 'Electronics', 'keyboard,mechanical,product');

-- Archived products (3 items)
INSERT INTO products (id, slug, name, description, price, status, category, tags) VALUES
(gen_random_uuid(), 'legacy-widget-v1', 'Legacy Widget V1', 'Original widget product, now discontinued and archived.', 5.99, 'ARCHIVED', 'Electronics', 'widget,legacy,discontinued'),
(gen_random_uuid(), 'old-gadget-classic', 'Old Gadget Classic', 'Classic gadget product no longer in production.', 14.99, 'ARCHIVED', 'Electronics', 'gadget,classic,archived'),
(gen_random_uuid(), 'retired-tool-basic', 'Retired Tool Basic', 'Basic tool product that has been retired from the catalog.', 299.99, 'ARCHIVED', 'Tools', 'tool,retired,archived');
