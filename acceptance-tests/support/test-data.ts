export interface User {
  id?: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role?: 'customer' | 'admin';
}

export interface Product {
  id?: string;
  name: string;
  description: string;
  price: number;
  category: string;
  sku: string;
  inStock: boolean;
  quantity?: number;
}

export interface CartItem {
  productId: string;
  quantity: number;
}

export interface Order {
  id?: string;
  userId: string;
  items: CartItem[];
  status?: 'pending' | 'processing' | 'shipped' | 'delivered' | 'cancelled';
  total?: number;
}

export interface Address {
  street: string;
  city: string;
  state: string;
  zipCode: string;
  country: string;
}

let userCounter = 0;
let productCounter = 0;

export function generateUniqueEmail(prefix = 'test'): string {
  userCounter++;
  return `${prefix}+${Date.now()}-${userCounter}@example.com`;
}

export function generateUniqueSku(prefix = 'SKU'): string {
  productCounter++;
  return `${prefix}-${Date.now()}-${productCounter}`;
}

export const TestUsers = {
  customer: (): User => ({
    email: generateUniqueEmail('customer'),
    password: 'TestPassword123!',
    firstName: 'Test',
    lastName: 'Customer',
    role: 'customer',
  }),

  admin: (): User => ({
    email: generateUniqueEmail('admin'),
    password: 'AdminPassword123!',
    firstName: 'Test',
    lastName: 'Admin',
    role: 'admin',
  }),

  existingCustomer: (): User => ({
    email: 'existing.customer@example.com',
    password: 'ExistingPassword123!',
    firstName: 'Existing',
    lastName: 'Customer',
    role: 'customer',
  }),

  existingAdmin: (): User => ({
    email: 'existing.admin@example.com',
    password: 'ExistingPassword123!',
    firstName: 'Existing',
    lastName: 'Admin',
    role: 'admin',
  }),
};

export const TestProducts = {
  simple: (): Product => ({
    name: 'Test Product',
    description: 'A simple test product for testing purposes',
    price: 29.99,
    category: 'Electronics',
    sku: generateUniqueSku('SIMPLE'),
    inStock: true,
    quantity: 100,
  }),

  outOfStock: (): Product => ({
    name: 'Out of Stock Product',
    description: 'A product that is currently out of stock',
    price: 49.99,
    category: 'Electronics',
    sku: generateUniqueSku('OOS'),
    inStock: false,
    quantity: 0,
  }),

  expensive: (): Product => ({
    name: 'Premium Product',
    description: 'A high-end premium product',
    price: 999.99,
    category: 'Premium',
    sku: generateUniqueSku('PREMIUM'),
    inStock: true,
    quantity: 10,
  }),
};

export const TestAddresses = {
  valid: (): Address => ({
    street: '123 Test Street',
    city: 'Test City',
    state: 'TS',
    zipCode: '12345',
    country: 'United States',
  }),

  international: (): Address => ({
    street: '456 International Ave',
    city: 'London',
    state: 'England',
    zipCode: 'SW1A 1AA',
    country: 'United Kingdom',
  }),
};

export function createTestOrder(userId: string, items: CartItem[]): Order {
  return {
    userId,
    items,
    status: 'pending',
  };
}

export function resetCounters(): void {
  userCounter = 0;
  productCounter = 0;
}
