import {
  Box,
  MenuItem,
  Pagination as MuiPagination,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import type { PageMeta } from '../api/types';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

interface PaginationProps {
  /** Page metadata from the API (0-based `number`). */
  meta: PageMeta;
  /** Called with a 1-based page number. */
  onPageChange(page: number): void;
  onSizeChange(size: number): void;
  itemLabel?: string;
}

export function Pagination({
  meta,
  onPageChange,
  onSizeChange,
  itemLabel = 'total',
}: PaginationProps) {
  const pageCount = Math.max(meta.totalPages, 1);
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      alignItems="center"
      justifyContent="space-between"
      sx={{ px: 2, py: 1.5 }}
    >
      <Typography variant="body2" color="text.secondary">
        {meta.totalElements.toLocaleString()} {itemLabel}
      </Typography>
      <MuiPagination
        count={pageCount}
        page={Math.min(meta.number + 1, pageCount)}
        onChange={(_e, page) => onPageChange(page)}
        color="primary"
        shape="rounded"
        siblingCount={1}
        showFirstButton
        showLastButton
      />
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="body2" color="text.secondary" component="label" htmlFor="page-size">
          Rows
        </Typography>
        <Select
          size="small"
          value={meta.size}
          onChange={(e) => onSizeChange(Number(e.target.value))}
          inputProps={{ id: 'page-size', 'aria-label': 'Rows per page' }}
          sx={{ minWidth: 72 }}
        >
          {PAGE_SIZE_OPTIONS.map((n) => (
            <MenuItem key={n} value={n}>
              {n}
            </MenuItem>
          ))}
        </Select>
      </Box>
    </Stack>
  );
}
