import { describe, expect, it } from 'vitest';
import { useUi } from '../store/ui';

describe('UI state',()=>{it('updates navigation and agent state deterministically',()=>{useUi.getState().setCollapsed(true);useUi.getState().openAgent({currentDocId:'doc-1'});expect(useUi.getState()).toMatchObject({collapsed:true,agentOpen:true,agentContext:{currentDocId:'doc-1'}});useUi.getState().setAgentOpen(false);useUi.getState().setAgentOpen(true);expect(useUi.getState()).toMatchObject({agentOpen:true,agentContext:{}});useUi.getState().setCollapsed(false);useUi.getState().setAgentOpen(false);expect(useUi.getState()).toMatchObject({collapsed:false,agentOpen:false});});});
