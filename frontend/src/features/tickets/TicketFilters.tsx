import ClearIcon from '@mui/icons-material/Clear';
import FilterAltOffOutlinedIcon from '@mui/icons-material/FilterAltOffOutlined';
import SearchIcon from '@mui/icons-material/Search';
import {
  Box,
  Button,
  Checkbox,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  ListItemText,
  ListSubheader,
  MenuItem,
  OutlinedInput,
  Select,
  TextField,
  type SelectChangeEvent,
} from '@mui/material';
import { useEffect, useRef, useState } from 'react';
import {
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  type TicketPriority,
  type TicketStatus,
} from '../../api/types';
import { PRIORITY_LABELS, STATUS_LABELS } from '../../utils/labels';
import { useAgents } from './api';
import { DEFAULT_TICKET_LIST_STATE, isFiltered, useTicketListState } from './ticketListState';

export const SEARCH_DEBOUNCE_MS = 300;

interface TicketFiltersProps {
  /** Staff only: customers never see the assignee filter. */
  showAssignee: boolean;
}

function multiValue<T extends string>(event: SelectChangeEvent<T[]>): T[] {
  const value = event.target.value;
  return (typeof value === 'string' ? value.split(',') : value) as T[];
}

export function TicketFilters({ showAssignee }: TicketFiltersProps) {
  const [state, setState] = useTicketListState();
  const agentsQuery = useAgents(showAssignee);

  // Local echo of the search box; committed to the URL after a short pause.
  const [search, setSearch] = useState(state.q);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // react-router's setSearchParams closes over the params of the render that created it,
  // so the debounced commit must call the latest one.
  const setStateRef = useRef(setState);
  useEffect(() => {
    setStateRef.current = setState;
  }, [setState]);

  useEffect(() => {
    // URL changed from outside (reset, back button) – reflect it unless the user is typing.
    if (!timer.current) setSearch(state.q);
  }, [state.q]);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const commitSearch = (value: string) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    if (value.trim() !== state.q.trim()) setState({ q: value, page: 1 }, { replace: true });
  };

  const onSearchChange = (value: string) => {
    setSearch(value);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      timer.current = null;
      setStateRef.current({ q: value, page: 1 }, { replace: true });
    }, SEARCH_DEBOUNCE_MS);
  };

  const reset = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    setSearch('');
    setState((prev) => ({
      ...DEFAULT_TICKET_LIST_STATE,
      size: prev.size,
      sortField: prev.sortField,
      sortDir: prev.sortDir,
    }));
  };

  const agents = agentsQuery.data ?? [];
  const specificAgentUnknown =
    state.assignee !== 'any' &&
    state.assignee !== 'unassigned' &&
    state.assignee !== 'me' &&
    !agents.some((a) => a.id === state.assignee);

  return (
    <Box
      role="search"
      aria-label="Ticket filters"
      sx={{
        display: 'grid',
        gap: 1.5,
        gridTemplateColumns: {
          xs: '1fr',
          sm: '1fr 1fr',
          md: showAssignee ? '2fr 1.2fr 1fr 1.2fr auto' : '2fr 1.2fr 1fr auto',
        },
        alignItems: 'center',
      }}
    >
      <TextField
        size="small"
        label="Search"
        placeholder="Subject, description or #number"
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commitSearch(search);
        }}
        sx={{ gridColumn: { sm: '1 / -1', md: 'auto' } }}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
            endAdornment: search ? (
              <InputAdornment position="end">
                <IconButton
                  size="small"
                  aria-label="Clear search"
                  onClick={() => {
                    setSearch('');
                    commitSearch('');
                  }}
                >
                  <ClearIcon fontSize="small" />
                </IconButton>
              </InputAdornment>
            ) : undefined,
          },
        }}
      />

      <FormControl size="small">
        <InputLabel id="filter-status-label" shrink>Status</InputLabel>
        <Select<TicketStatus[]>
          labelId="filter-status-label"
          id="filter-status"
          multiple
          value={state.status}
          onChange={(e) => setState({ status: multiValue(e), page: 1 })}
          input={<OutlinedInput label="Status" notched />}
          renderValue={(selected) =>
            selected.length === 0 ? 'All' : selected.map((s) => STATUS_LABELS[s]).join(', ')
          }
          displayEmpty
        >
          {TICKET_STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              <Checkbox size="small" checked={state.status.includes(s)} />
              <ListItemText primary={STATUS_LABELS[s]} />
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      <FormControl size="small">
        <InputLabel id="filter-priority-label" shrink>Priority</InputLabel>
        <Select<TicketPriority[]>
          labelId="filter-priority-label"
          id="filter-priority"
          multiple
          value={state.priority}
          onChange={(e) => setState({ priority: multiValue(e), page: 1 })}
          input={<OutlinedInput label="Priority" notched />}
          renderValue={(selected) =>
            selected.length === 0 ? 'All' : selected.map((p) => PRIORITY_LABELS[p]).join(', ')
          }
          displayEmpty
        >
          {TICKET_PRIORITIES.map((p) => (
            <MenuItem key={p} value={p}>
              <Checkbox size="small" checked={state.priority.includes(p)} />
              <ListItemText primary={PRIORITY_LABELS[p]} />
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {showAssignee && (
        <FormControl size="small">
          <InputLabel id="filter-assignee-label">Assignee</InputLabel>
          <Select<string>
            labelId="filter-assignee-label"
            id="filter-assignee"
            label="Assignee"
            value={state.assignee}
            onChange={(e) => setState({ assignee: e.target.value, page: 1 })}
          >
            <MenuItem value="any">Anyone</MenuItem>
            <MenuItem value="unassigned">Unassigned</MenuItem>
            <MenuItem value="me">Assigned to me</MenuItem>
            {agents.length > 0 && <ListSubheader>Agents</ListSubheader>}
            {agents.map((a) => (
              <MenuItem key={a.id} value={a.id}>
                {a.fullName}
              </MenuItem>
            ))}
            {specificAgentUnknown && (
              <MenuItem value={state.assignee} sx={{ display: 'none' }}>
                Selected agent
              </MenuItem>
            )}
          </Select>
        </FormControl>
      )}

      <Button
        variant="text"
        color="inherit"
        startIcon={<FilterAltOffOutlinedIcon />}
        onClick={reset}
        disabled={!isFiltered(state) && !search}
      >
        Reset
      </Button>
    </Box>
  );
}
