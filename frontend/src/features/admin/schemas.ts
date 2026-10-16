import { z } from 'zod';
import { ROLES } from '../../api/types';

export const fullNameSchema = z
  .string()
  .trim()
  .min(1, 'Full name is required')
  .max(120, 'Full name must be at most 120 characters');

export const emailSchema = z
  .string()
  .trim()
  .min(1, 'Email is required')
  .max(254, 'Email must be at most 254 characters')
  .email('Enter a valid email address');

export const passwordSchema = z
  .string()
  .min(8, 'Password must be at least 8 characters')
  .max(72, 'Password must be at most 72 characters');

export const createUserSchema = z.object({
  fullName: fullNameSchema,
  email: emailSchema,
  password: passwordSchema,
  role: z.enum(ROLES),
  active: z.boolean(),
});

export const editUserSchema = z.object({
  fullName: fullNameSchema,
  email: z.string(),
  password: z.string(),
  role: z.enum(ROLES),
  active: z.boolean(),
});

export type UserForm = z.infer<typeof createUserSchema>;

export const organizationSchema = z.object({
  name: z
    .string()
    .trim()
    .min(2, 'Name must be at least 2 characters')
    .max(120, 'Name must be at most 120 characters'),
});
export type OrganizationForm = z.infer<typeof organizationSchema>;
