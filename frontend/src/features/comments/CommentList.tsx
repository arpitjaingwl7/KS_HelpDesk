import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import LockIcon from '@mui/icons-material/Lock';
import {
  Avatar,
  Box,
  Button,
  Chip,
  Paper,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import type { Comment } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorAlert';
import { formatDateTime, formatRelative } from '../../utils/formatDate';
import { initials, ROLE_LABELS } from '../../utils/labels';
import { useComments } from './api';

export const INTERNAL_NOTE_BG = '#fff8e1';
export const INTERNAL_NOTE_BORDER = '#f5c451';

export function CommentItem({ comment }: { comment: Comment }) {
  const internal = comment.visibility === 'INTERNAL';
  const staffAuthor = comment.author.role !== 'CUSTOMER';
  return (
    <Paper
      variant="outlined"
      component="article"
      aria-label={
        internal
          ? `Internal note by ${comment.author.fullName}`
          : `Comment by ${comment.author.fullName}`
      }
      sx={{
        p: 2,
        bgcolor: internal ? INTERNAL_NOTE_BG : 'background.paper',
        borderColor: internal ? INTERNAL_NOTE_BORDER : 'divider',
        borderLeftWidth: 3,
        borderLeftColor: internal
          ? INTERNAL_NOTE_BORDER
          : staffAuthor
            ? 'primary.light'
            : 'divider',
      }}
    >
      <Stack
        direction="row"
        spacing={1.5}
        alignItems="center"
        sx={{ mb: 1, flexWrap: 'wrap', rowGap: 0.5 }}
      >
        <Avatar
          sx={{
            width: 28,
            height: 28,
            fontSize: 12,
            bgcolor: staffAuthor ? 'primary.main' : 'grey.500',
          }}
        >
          {initials(comment.author.fullName)}
        </Avatar>
        {internal && (
          <Chip
            size="small"
            icon={<LockIcon sx={{ fontSize: 14 }} />}
            label="Internal note"
            sx={{
              bgcolor: '#ffe7a3',
              color: '#7a5600',
              fontWeight: 700,
              '& .MuiChip-icon': { color: '#7a5600' },
            }}
          />
        )}
        <Typography variant="subtitle2">{comment.author.fullName}</Typography>
        <Typography variant="caption" color="text.secondary">
          {ROLE_LABELS[comment.author.role]}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          ·
        </Typography>
        <Tooltip title={formatDateTime(comment.createdAt)}>
          <Typography
            variant="caption"
            color="text.secondary"
            component="time"
            dateTime={comment.createdAt}
          >
            {formatRelative(comment.createdAt)}
          </Typography>
        </Tooltip>
      </Stack>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
        {comment.body}
      </Typography>
    </Paper>
  );
}

export function CommentList({ ticketId }: { ticketId: string }) {
  const query = useComments(ticketId);

  if (query.isPending) {
    return (
      <Stack spacing={1.5} aria-busy>
        {[0, 1].map((i) => (
          <Skeleton key={i} variant="rounded" height={88} />
        ))}
      </Stack>
    );
  }
  if (query.isError) {
    return (
      <ErrorAlert
        error={query.error}
        title="Couldn't load comments"
        onRetry={() => void query.refetch()}
      />
    );
  }

  const comments = query.data.pages.flatMap((p) => p.content);
  if (comments.length === 0) {
    return (
      <EmptyState
        icon={<ChatBubbleOutlineIcon fontSize="inherit" />}
        title="No replies yet"
        description="Start the conversation below."
      />
    );
  }

  return (
    <Stack spacing={1.5}>
      {comments.map((c) => (
        <CommentItem key={c.id} comment={c} />
      ))}
      {query.hasNextPage && (
        <Box sx={{ textAlign: 'center' }}>
          <Button onClick={() => void query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {query.isFetchingNextPage ? 'Loading…' : 'Load more'}
          </Button>
        </Box>
      )}
    </Stack>
  );
}
