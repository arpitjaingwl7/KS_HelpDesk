import { alpha, createTheme } from '@mui/material/styles';

const primary = '#4338ca';

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: primary, light: '#6366f1', dark: '#3730a3' },
    secondary: { main: '#0d9488', light: '#2dd4bf' },
    background: { default: '#f6f7fb', paper: '#ffffff' },
    text: { primary: '#0f172a', secondary: '#475569' },
    divider: '#e2e8f0',
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily:
      '"Inter", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    h4: { fontWeight: 700, letterSpacing: -0.5 },
    h5: { fontWeight: 700, letterSpacing: -0.25 },
    h6: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
    overline: { fontWeight: 700, letterSpacing: 0.8 },
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
    },
    MuiPaper: {
      styleOverrides: { outlined: { borderColor: '#e2e8f0' } },
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          fontWeight: 600,
          color: '#475569',
          backgroundColor: '#f8fafc',
          fontSize: '0.8rem',
          textTransform: 'uppercase',
          letterSpacing: 0.4,
        },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: { '&.MuiTableRow-hover:hover': { backgroundColor: alpha(primary, 0.04) } },
      },
    },
    MuiTextField: { defaultProps: { variant: 'outlined' } },
    // Forms use react-hook-form's uncontrolled register(); values set via setValue/reset or by browser
    // autofill don't reach MUI's "filled" state, so a floating label would sit on top of the text.
    // Keeping labels always shrunk (with a notched outline) makes overlap impossible everywhere.
    MuiInputLabel: { defaultProps: { shrink: true } },
    MuiOutlinedInput: { defaultProps: { notched: true } },
    MuiChip: { styleOverrides: { root: { fontWeight: 500 } } },
    MuiTab: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600, minHeight: 52 } } },
    MuiAlert: { styleOverrides: { root: { alignItems: 'center' } } },
    MuiDialogTitle: { styleOverrides: { root: { fontWeight: 700 } } },
  },
});
