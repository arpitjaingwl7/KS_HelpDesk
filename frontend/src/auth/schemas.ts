import { z } from 'zod';
import { emailSchema, fullNameSchema, passwordSchema } from '../features/admin/schemas';

export const SLUG_REGEX = /^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$/;

export const slugSchema = z
  .string()
  .trim()
  .min(1, 'Organization slug is required')
  .regex(
    SLUG_REGEX,
    '3–50 lowercase letters, digits or hyphens; must start and end with a letter or digit',
  );

export const loginSchema = z.object({
  organizationSlug: z.string().trim().min(1, 'Organization is required'),
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});
export type LoginForm = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  organizationName: z
    .string()
    .trim()
    .min(2, 'Name must be at least 2 characters')
    .max(120, 'Name must be at most 120 characters'),
  organizationSlug: slugSchema,
  adminFullName: fullNameSchema,
  adminEmail: emailSchema,
  adminPassword: passwordSchema,
});
export type RegisterForm = z.infer<typeof registerSchema>;

/** "Acme Corp!" → "acme-corp" (best-effort suggestion while the user types the name). */
export function slugify(name: string): string {
  return name
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 50)
    .replace(/-+$/g, '');
}
