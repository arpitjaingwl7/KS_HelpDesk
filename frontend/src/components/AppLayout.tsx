import ConfirmationNumberOutlinedIcon from '@mui/icons-material/ConfirmationNumberOutlined';
import BusinessOutlinedIcon from '@mui/icons-material/BusinessOutlined';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import HeadsetMicOutlinedIcon from '@mui/icons-material/HeadsetMicOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import MenuIcon from '@mui/icons-material/Menu';
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Container,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import { Suspense, useState, type ReactNode } from 'react';
import { NavLink, Outlet, Link as RouterLink, useLocation } from 'react-router-dom';
import type { Role } from '../api/types';
import { useAuth } from '../auth/useAuth';
import { initials, ROLE_LABELS } from '../utils/labels';
import { RouteFallback } from './RouteFallback';

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { to: '/tickets', label: 'Tickets', icon: <ConfirmationNumberOutlinedIcon /> },
  { to: '/admin/users', label: 'Users', icon: <GroupOutlinedIcon />, roles: ['ORG_ADMIN'] },
  {
    to: '/admin/organization',
    label: 'Organization',
    icon: <BusinessOutlinedIcon />,
    roles: ['ORG_ADMIN'],
  },
];

export function AppLayout() {
  const { user, logout, hasRole } = useAuth();
  const location = useLocation();
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const items = NAV_ITEMS.filter((item) => !item.roles || hasRole(...item.roles));
  const isActive = (to: string) =>
    location.pathname === to || location.pathname.startsWith(`${to}/`);

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        bgcolor: 'background.default',
      }}
    >
      <AppBar
        position="sticky"
        color="inherit"
        elevation={0}
        sx={{ borderBottom: 1, borderColor: 'divider' }}
      >
        <Toolbar sx={{ gap: 1 }}>
          <IconButton
            edge="start"
            aria-label="Open navigation"
            onClick={() => setDrawerOpen(true)}
            sx={{ display: { xs: 'inline-flex', md: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Box
            component={RouterLink}
            to="/tickets"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              color: 'inherit',
              textDecoration: 'none',
              mr: 3,
              minWidth: 0,
            }}
          >
            <Avatar variant="rounded" sx={{ bgcolor: 'primary.main', width: 32, height: 32 }}>
              <HeadsetMicOutlinedIcon fontSize="small" />
            </Avatar>
            <Typography variant="h6" noWrap sx={{ fontWeight: 700 }}>
              Helpdesk
            </Typography>
            {user && (
              <Typography
                variant="body2"
                color="text.secondary"
                noWrap
                sx={{ display: { xs: 'none', sm: 'block' } }}
              >
                · {user.organization.name}
              </Typography>
            )}
          </Box>

          <Box
            component="nav"
            aria-label="Main"
            sx={{ display: { xs: 'none', md: 'flex' }, gap: 0.5 }}
          >
            {items.map((item) => (
              <Button
                key={item.to}
                component={NavLink}
                to={item.to}
                startIcon={item.icon}
                color={isActive(item.to) ? 'primary' : 'inherit'}
                sx={{
                  fontWeight: isActive(item.to) ? 700 : 500,
                  bgcolor: isActive(item.to) ? 'action.selected' : 'transparent',
                }}
              >
                {item.label}
              </Button>
            ))}
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          {user && (
            <>
              <Tooltip title="Account">
                <Button
                  color="inherit"
                  onClick={(e) => setMenuAnchor(e.currentTarget)}
                  aria-haspopup="menu"
                  aria-controls={menuAnchor ? 'account-menu' : undefined}
                  sx={{ textTransform: 'none', gap: 1 }}
                >
                  <Avatar sx={{ width: 32, height: 32, fontSize: 14, bgcolor: 'secondary.main' }}>
                    {initials(user.fullName)}
                  </Avatar>
                  <Box
                    sx={{
                      display: { xs: 'none', sm: 'block' },
                      textAlign: 'left',
                      lineHeight: 1.2,
                    }}
                  >
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {user.fullName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {ROLE_LABELS[user.role]}
                    </Typography>
                  </Box>
                </Button>
              </Tooltip>
              <Menu
                id="account-menu"
                anchorEl={menuAnchor}
                open={Boolean(menuAnchor)}
                onClose={() => setMenuAnchor(null)}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                transformOrigin={{ vertical: 'top', horizontal: 'right' }}
              >
                <Box sx={{ px: 2, py: 1 }}>
                  <Typography variant="subtitle2">{user.fullName}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {user.email}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {ROLE_LABELS[user.role]} · {user.organization.slug}
                  </Typography>
                </Box>
                <Divider />
                <MenuItem
                  onClick={() => {
                    setMenuAnchor(null);
                    logout();
                  }}
                >
                  <ListItemIcon>
                    <LogoutIcon fontSize="small" />
                  </ListItemIcon>
                  Log out
                </MenuItem>
              </Menu>
            </>
          )}
        </Toolbar>
      </AppBar>

      <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box sx={{ width: 260 }} role="presentation" onClick={() => setDrawerOpen(false)}>
          <Toolbar>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Helpdesk
            </Typography>
          </Toolbar>
          <Divider />
          <List component="nav" aria-label="Main">
            {items.map((item) => (
              <ListItemButton
                key={item.to}
                component={NavLink}
                to={item.to}
                selected={isActive(item.to)}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
          </List>
        </Box>
      </Drawer>

      <Container component="main" maxWidth="xl" sx={{ flexGrow: 1, py: { xs: 2, md: 4 } }}>
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
      </Container>
    </Box>
  );
}
