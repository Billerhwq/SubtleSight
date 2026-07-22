import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { StatePanel } from '../components/StatePanel';

describe('StatePanel',()=>{it('renders loading, empty and content states',()=>{const {rerender}=render(<StatePanel loading><div>content</div></StatePanel>);expect(screen.getByText('正在整理情报…')).toBeInTheDocument();rerender(<StatePanel empty><div>content</div></StatePanel>);expect(screen.getByText('还没有情报')).toBeInTheDocument();rerender(<StatePanel><div>content</div></StatePanel>);expect(screen.getByText('content')).toBeInTheDocument();});it('renders provider errors and retries',()=>{const retry=vi.fn();render(<StatePanel error={new Error('provider degraded')} onRetry={retry}><div/></StatePanel>);expect(screen.getByText('provider degraded')).toBeInTheDocument();fireEvent.click(screen.getByText('重试'));expect(retry).toHaveBeenCalledOnce();});});
