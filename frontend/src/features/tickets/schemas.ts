import { z } from 'zod';
import { TICKET_PRIORITIES } from '../../api/types';

/** Mirrors the backend bean validation for tickets (docs/04 §3.4). */
export const subjectSchema = z
  .string()
  .trim()
  .min(3, 'Subject must be at least 3 characters')
  .max(200, 'Subject must be at most 200 characters');

export const descriptionSchema = z
  .string()
  .refine((v) => v.trim().length > 0, 'Description is required')
  .pipe(z.string().max(10_000, 'Description must be at most 10,000 characters'));

export const createTicketSchema = z.object({
  subject: subjectSchema,
  description: descriptionSchema,
  priority: z.enum(TICKET_PRIORITIES),
  requesterId: z.string().uuid('Pick a customer from the list').nullable(),
});

export type CreateTicketForm = z.infer<typeof createTicketSchema>;

export const editTicketSchema = z.object({
  subject: subjectSchema,
  description: descriptionSchema,
});

export type EditTicketForm = z.infer<typeof editTicketSchema>;
