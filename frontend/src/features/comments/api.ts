import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import { queryKeys } from '../../api/queryKeys';
import type { Comment, CreateCommentRequest, Page } from '../../api/types';

export const COMMENTS_PAGE_SIZE = 50;

export async function fetchComments(ticketId: string, page: number): Promise<Page<Comment>> {
  const { data } = await api.get<Page<Comment>>(
    `/tickets/${encodeURIComponent(ticketId)}/comments`,
    {
      params: { page, size: COMMENTS_PAGE_SIZE },
    },
  );
  return data;
}

/** Oldest first; further pages are appended with "Load more". */
export function useComments(ticketId: string) {
  return useInfiniteQuery({
    queryKey: queryKeys.comments(ticketId),
    queryFn: ({ pageParam }) => fetchComments(ticketId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.page.number + 1 < last.page.totalPages ? last.page.number + 1 : undefined,
  });
}

export function useAddComment(ticketId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateCommentRequest) => {
      const { data } = await api.post<Comment>(
        `/tickets/${encodeURIComponent(ticketId)}/comments`,
        body,
      );
      return data;
    },
    onSuccess: (comment) => {
      // Optimistically append, then refetch to get authoritative paging.
      queryClient.setQueryData<InfiniteData<Page<Comment>, number>>(
        queryKeys.comments(ticketId),
        (prev) => {
          if (!prev || prev.pages.length === 0) return prev;
          const pages = [...prev.pages];
          const lastIndex = pages.length - 1;
          const last = pages[lastIndex];
          if (!last || last.content.some((c) => c.id === comment.id)) return prev;
          pages[lastIndex] = { ...last, content: [...last.content, comment] };
          return { ...prev, pages };
        },
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.comments(ticketId) });
      // Commenting bumps updatedAt (and may affect list ordering).
      void queryClient.invalidateQueries({ queryKey: queryKeys.ticket(ticketId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.ticketsAll });
    },
  });
}
