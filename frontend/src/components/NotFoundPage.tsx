import { StatusPage } from './StatusPage';

export function NotFoundPage() {
  return (
    <StatusPage
      code="404"
      title="Page not found"
      description="The page you're looking for doesn't exist or has moved."
    />
  );
}
