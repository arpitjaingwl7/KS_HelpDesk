import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import PersonOffOutlinedIcon from '@mui/icons-material/PersonOffOutlined';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import SearchIcon from '@mui/icons-material/Search';
import {
  Avatar,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  InputAdornment,
  LinearProgress,
  MenuItem,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { isGloballyHandled, problemMessage } from '../../api/problem';
import { ROLES, type Role, type UserFilters, type UserSummary } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorAlert';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { useToast } from '../../components/useToast';
import { formatDate } from '../../utils/formatDate';
import { initials, ROLE_LABELS } from '../../utils/labels';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import {
  parseEnum,
  parsePositiveInt,
  useUrlState,
  type UrlStateCodec,
} from '../../utils/useUrlState';
import { useSetUserActive, useUsers } from './api';
import { UserFormDialog } from './UserFormDialog';

interface UsersListState {
  q: string;
  role: Role | undefined;
  active: 'all' | 'true' | 'false';
  page: number;
  size: number;
}

const usersCodec: UrlStateCodec<UsersListState> = {
  parse: (p) => ({
    q: p.get('q') ?? '',
    role: parseEnum(p.get('role'), ROLES),
    active: parseEnum(p.get('active'), ['true', 'false'] as const) ?? 'all',
    page: parsePositiveInt(p.get('page'), 1),
    size: parsePositiveInt(p.get('size'), 20, 100),
  }),
  serialize: (s) => {
    const p = new URLSearchParams();
    if (s.q.trim()) p.set('q', s.q.trim());
    if (s.role) p.set('role', s.role);
    if (s.active !== 'all') p.set('active', s.active);
    if (s.page > 1) p.set('page', String(s.page));
    if (s.size !== 20) p.set('size', String(s.size));
    return p;
  },
};
const USERS_KEYS = ['q', 'role', 'active', 'page', 'size'] as const;

const ROLE_COLORS: Record<Role, 'secondary' | 'primary' | 'default'> = {
  ORG_ADMIN: 'secondary',
  AGENT: 'primary',
  CUSTOMER: 'default',
};

export function UsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const [state, setState] = useUrlState(usersCodec, USERS_KEYS);
  const [search, setSearch] = useState(state.q);
  const debouncedSearch = useDebouncedValue(search, 300);
  const [dialog, setDialog] = useState<{ open: boolean; userId: string | null }>({
    open: false,
    userId: null,
  });
  const [toggleTarget, setToggleTarget] = useState<UserSummary | null>(null);
  const setActive = useSetUserActive();

  useEffect(() => {
    if (debouncedSearch.trim() !== state.q.trim())
      setState({ q: debouncedSearch, page: 1 }, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- react only to the debounced input
  }, [debouncedSearch]);

  const filters = useMemo<UserFilters>(
    () => ({
      ...(state.q.trim() ? { q: state.q.trim() } : {}),
      ...(state.role ? { role: state.role } : {}),
      ...(state.active !== 'all' ? { active: state.active === 'true' } : {}),
      page: state.page - 1,
      size: state.size,
      sort: 'fullName,asc',
    }),
    [state],
  );
  const usersQuery = useUsers(filters);
  const users = usersQuery.data?.content ?? [];
  const page = usersQuery.data?.page;
  const loading = usersQuery.isPending;
  const filtered = Boolean(state.q.trim() || state.role || state.active !== 'all');

  const confirmToggle = () => {
    if (!toggleTarget) return;
    const nextActive = !toggleTarget.active;
    setActive.mutate(
      { id: toggleTarget.id, active: nextActive },
      {
        onSuccess: (u) => {
          toast.success(`${u.fullName} ${u.active ? 'reactivated' : 'deactivated'}`);
          setToggleTarget(null);
        },
        onError: (err) => {
          if (!isGloballyHandled(err)) toast.error(problemMessage(err));
          setToggleTarget(null);
        },
      },
    );
  };

  return (
    <>
      <PageHeader
        title="Users"
        subtitle="Manage who can access your helpdesk"
        actions={
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setDialog({ open: true, userId: null })}
          >
            New user
          </Button>
        }
      />

      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ p: 2 }}>
          <TextField
            size="small"
            label="Search"
            placeholder="Name or email"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            sx={{ flex: 2 }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              },
            }}
          />
          <TextField
            select
            size="small"
            label="Role"
            value={state.role ?? ''}
            slotProps={{ select: { displayEmpty: true } }}
            onChange={(e) =>
              setState({ role: (e.target.value || undefined) as Role | undefined, page: 1 })
            }
            sx={{ flex: 1, minWidth: 140 }}
          >
            <MenuItem value="">All</MenuItem>
            {ROLES.map((r) => (
              <MenuItem key={r} value={r}>
                {ROLE_LABELS[r]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="Status"
            value={state.active}
            onChange={(e) =>
              setState({ active: e.target.value as UsersListState['active'], page: 1 })
            }
            sx={{ flex: 1, minWidth: 140 }}
          >
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
        </Stack>
        <Box sx={{ height: 4 }}>{usersQuery.isFetching && !loading && <LinearProgress />}</Box>
        <Divider />

        {usersQuery.isError ? (
          <Box sx={{ p: 2 }}>
            <ErrorAlert
              error={usersQuery.error}
              title="Couldn't load users"
              onRetry={() => void usersQuery.refetch()}
            />
          </Box>
        ) : (
          <TableContainer>
            <Table aria-label="Users">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Email</TableCell>
                  <TableCell>Role</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>Created</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading
                  ? Array.from({ length: 5 }).map((_, i) => (
                      <TableRow key={i}>
                        {Array.from({ length: 6 }).map((__, j) => (
                          <TableCell key={j}>
                            <Skeleton variant="text" />
                          </TableCell>
                        ))}
                      </TableRow>
                    ))
                  : users.map((u) => (
                      <TableRow key={u.id} hover sx={{ opacity: u.active ? 1 : 0.6 }}>
                        <TableCell>
                          <Stack direction="row" spacing={1.5} alignItems="center">
                            <Avatar sx={{ width: 32, height: 32, fontSize: 13 }}>
                              {initials(u.fullName)}
                            </Avatar>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {u.fullName}
                                {u.id === me?.id && (
                                  <Typography
                                    component="span"
                                    variant="caption"
                                    color="text.secondary"
                                  >
                                    {' '}
                                    (you)
                                  </Typography>
                                )}
                              </Typography>
                              <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{
                                  display: { xs: 'block', md: 'none' },
                                  overflowWrap: 'anywhere',
                                }}
                              >
                                {u.email}
                              </Typography>
                            </Box>
                          </Stack>
                        </TableCell>
                        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                          {u.email}
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={ROLE_LABELS[u.role]}
                            color={ROLE_COLORS[u.role]}
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={u.active ? 'Active' : 'Inactive'}
                            color={u.active ? 'success' : 'default'}
                            variant={u.active ? 'filled' : 'outlined'}
                          />
                        </TableCell>
                        <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                          {formatDate(u.createdAt)}
                        </TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          <Tooltip title="Edit">
                            <IconButton
                              aria-label={`Edit ${u.fullName}`}
                              onClick={() => setDialog({ open: true, userId: u.id })}
                            >
                              <EditOutlinedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title={u.active ? 'Deactivate' : 'Reactivate'}>
                            <IconButton
                              aria-label={`${u.active ? 'Deactivate' : 'Reactivate'} ${u.fullName}`}
                              onClick={() => setToggleTarget(u)}
                            >
                              {u.active ? (
                                <PersonOffOutlinedIcon fontSize="small" />
                              ) : (
                                <PersonOutlineIcon fontSize="small" />
                              )}
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                {!loading && users.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} sx={{ borderBottom: 0 }}>
                      <EmptyState
                        icon={<GroupOutlinedIcon fontSize="inherit" />}
                        title={filtered ? 'No users match your filters' : 'No users yet'}
                        action={
                          <Button
                            variant="contained"
                            startIcon={<AddIcon />}
                            onClick={() => setDialog({ open: true, userId: null })}
                          >
                            Add a user
                          </Button>
                        }
                      />
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        {page && page.totalElements > 0 && (
          <>
            <Divider />
            <Pagination
              meta={page}
              itemLabel={page.totalElements === 1 ? 'user' : 'users'}
              onPageChange={(p) => setState({ page: p })}
              onSizeChange={(size) => setState({ size, page: 1 })}
            />
          </>
        )}
      </Paper>

      <UserFormDialog
        open={dialog.open}
        userId={dialog.userId}
        onClose={() => setDialog((d) => ({ ...d, open: false }))}
      />

      <ConfirmDialog
        open={toggleTarget !== null}
        title={toggleTarget?.active ? 'Deactivate user?' : 'Reactivate user?'}
        message={
          toggleTarget?.active
            ? `${toggleTarget.fullName} will be signed out and won't be able to sign in. Their tickets stay assigned.`
            : `${toggleTarget?.fullName ?? ''} will be able to sign in again.`
        }
        confirmLabel={toggleTarget?.active ? 'Deactivate' : 'Reactivate'}
        confirmColor={toggleTarget?.active ? 'error' : 'primary'}
        loading={setActive.isPending}
        onConfirm={confirmToggle}
        onClose={() => setToggleTarget(null)}
      />
    </>
  );
}
