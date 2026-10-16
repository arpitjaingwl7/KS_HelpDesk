import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { StatusPage } from './StatusPage';

export function NotAuthorizedPage() {
  return (
    <StatusPage
      icon={<LockOutlinedIcon fontSize="inherit" />}
      title="Not authorized"
      description="You don't have permission to view this page. Ask an organization admin if you think this is a mistake."
    />
  );
}
