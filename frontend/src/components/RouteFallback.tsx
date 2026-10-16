import { Box, LinearProgress } from '@mui/material';

export function RouteFallback() {
  return (
    <Box sx={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: (t) => t.zIndex.appBar + 1 }}>
      <LinearProgress aria-label="Loading page" />
    </Box>
  );
}
