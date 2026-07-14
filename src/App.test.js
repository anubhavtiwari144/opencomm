import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

test('renders OpenComm live room controls', () => {
  render(<App />);
  expect(screen.getByText(/OpenComm/i)).toBeInTheDocument();
  expect(screen.getByText(/Host view/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Generate Invite Link/i })).toBeInTheDocument();
});

test('shows a loading state while creating a room', async () => {
  const pendingResponse = new Promise(() => {});
  global.fetch = jest.fn(() => pendingResponse);

  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: /Generate Invite Link/i }));

  expect(screen.getByTestId('room-loading')).toBeInTheDocument();
});
