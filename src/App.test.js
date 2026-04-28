import { render, screen } from '@testing-library/react';
import App from './App';

test('renders OpenComm live room controls', () => {
  render(<App />);
  expect(screen.getByText(/OpenComm/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Generate Invite Link/i })).toBeInTheDocument();
});
