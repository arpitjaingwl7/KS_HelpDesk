import { Autocomplete, Box, CircularProgress, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import type { UserOption } from '../../api/types';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { useCustomers } from './api';

interface CustomerAutocompleteProps {
  value: UserOption | null;
  onChange(value: UserOption | null): void;
  error?: string;
  disabled?: boolean;
}

/** Async search over GET /customers?q= (staff only). */
export function CustomerAutocomplete({
  value,
  onChange,
  error,
  disabled,
}: CustomerAutocompleteProps) {
  const [input, setInput] = useState('');
  const [open, setOpen] = useState(false);
  const q = useDebouncedValue(input, 250);
  const customersQuery = useCustomers(q, open);
  const options = customersQuery.data ?? [];

  return (
    <Autocomplete<UserOption, false, false, false>
      open={open}
      onOpen={() => setOpen(true)}
      onClose={() => setOpen(false)}
      value={value}
      onChange={(_e, v) => onChange(v)}
      inputValue={input}
      onInputChange={(_e, v) => setInput(v)}
      options={value && !options.some((o) => o.id === value.id) ? [value, ...options] : options}
      filterOptions={(x) => x}
      getOptionLabel={(o) => o.fullName}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      loading={customersQuery.isFetching}
      disabled={disabled}
      noOptionsText={q ? 'No matching customers' : 'Type to search customers'}
      renderOption={(props, option) => {
        const { key, ...rest } = props as typeof props & { key: string };
        return (
          <Box component="li" key={key} {...rest}>
            <Box>
              <Typography variant="body2">{option.fullName}</Typography>
              <Typography variant="caption" color="text.secondary">
                {option.email}
              </Typography>
            </Box>
          </Box>
        );
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          label="On behalf of (optional)"
          placeholder="Search customers by name or email"
          error={Boolean(error)}
          helperText={error ?? 'Leave empty to raise the ticket as yourself'}
          slotProps={{
            input: {
              ...params.InputProps,
              endAdornment: (
                <>
                  {customersQuery.isFetching && <CircularProgress color="inherit" size={18} />}
                  {params.InputProps.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
    />
  );
}
