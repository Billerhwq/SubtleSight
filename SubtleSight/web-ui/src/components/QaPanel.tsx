import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, TextArea, Spin, Tag, Toast } from '@douyinfe/semi-ui';
import { IconSend, IconSearch, IconFile } from '@douyinfe/semi-icons';
import { useUi } from '../store/ui';
import { askQuestion, pollAnswer, saveAnswerAsDocument, type AnswerView, type QaCitation } from '../api/qa';

interface QaMessage {
  role: 'user' | 'assistant';
  question?: string;
  answer?: string;
  citations?: QaCitation[];
  resourceNames?: Record<string, string>;
  answerId?: string;
  status?: string;
  error?: string;
}

export function QaPanel(): ReactNode {
  const navigate = useNavigate();
  const setAgentOpen = useUi(s => s.setAgentOpen);
  const [question, setQuestion] = useState('');
  const [busy, setBusy] = useState(false);
  const [messages, setMessages] = useState<QaMessage[]>([]);

  const send = async () => {
    if (!question.trim() || busy) return;
    const q = question.trim();
    setMessages(v => [...v, { role: 'user', question: q }]);
    setQuestion('');
    setBusy(true);

    try {
      const start = await askQuestion(q, [{ type: 'KNOWLEDGE_BASE' }]);
      const answerId = start.answer.id;

      // Show queued status
      setMessages(v => [...v, { role: 'assistant', answer: '正在检索知识库…', answerId, status: 'QUEUED' }]);

      // Poll for completion
      const view = await pollAnswer(answerId, 30000);
      const citations = view.claims.flatMap(c => c.citations);

      // Replace queued message with final answer
      setMessages(v => {
        const copy = [...v];
        copy[copy.length - 1] = {
          role: 'assistant',
          answer: view.answer.directAnswer || '（未找到相关答案）',
          citations,
          resourceNames: view.resourceNames,
          answerId,
          status: view.answer.status,
        };
        return copy;
      });
    } catch (e) {
      setMessages(v => [...v, { role: 'assistant', error: (e as Error).message }]);
      Toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const handleSaveDoc = async (answerId: string, question: string) => {
    try {
      const doc = await saveAnswerAsDocument(answerId, question);
      Toast.success(`已保存为文档: ${doc.id.substring(0, 8)}...`);
    } catch (e) {
      Toast.error('保存失败: ' + (e as Error).message);
    }
  };

  const locatorLabel = (locatorJson: string): string => {
    try {
      const loc = JSON.parse(locatorJson);
      if (loc.blockId) return `段落 ${loc.blockId}`;
      if (loc.nodeId) return `节点 ${loc.nodeId}`;
      return loc.type ?? '引用';
    } catch {
      return '引用';
    }
  };

  const handleViewCitation = (c: QaCitation) => {
    const resourceType = c.resourceType?.toUpperCase();
    const resourceId = c.resourceId;
    // Close the agent drawer first so the knowledge page is visible
    setAgentOpen(false);
    try {
      const loc = JSON.parse(c.locatorJson || '{}');
      // Use setTimeout to let the drawer close animation finish before navigating
      setTimeout(() => {
        if (resourceType === 'DOCUMENT') {
          navigate(`/knowledge?documentId=${resourceId}${loc.blockId ? `&blockId=${loc.blockId}` : ''}`);
        } else if (resourceType === 'FILE') {
          navigate(`/knowledge?fileId=${resourceId}`);
        } else if (resourceType === 'DRAWING' || resourceType === 'DRAW_NODE') {
          navigate(`/knowledge?documentId=${resourceId}${loc.nodeId ? `&nodeId=${loc.nodeId}` : ''}`);
        } else {
          navigate('/knowledge');
        }
      }, 200);
    } catch {
      setTimeout(() => navigate('/knowledge'), 200);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Messages area */}
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 12 }}>
        {messages.length === 0 && (
          <div style={{ textAlign: 'center', padding: '2rem', color: '#98a2b3' }}>
            <IconSearch size="extra-large" />
            <p>基于知识库提问，AI 会给出带引用的可追溯回答</p>
            <p style={{ fontSize: 12 }}>支持追问和引用跳转</p>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} style={{ marginBottom: 16 }}>
            {m.role === 'user' && (
              <div style={{
                background: '#2f6fed', color: '#fff', padding: '10px 14px',
                borderRadius: 12, marginLeft: 'auto', maxWidth: '88%', width: 'fit-content',
              }}>
                {m.question}
              </div>
            )}
            {m.role === 'assistant' && (
              <div style={{
                background: m.error ? '#fef2f2' : '#f1f3f7',
                padding: 12, borderRadius: 12, maxWidth: '95%',
              }}>
                {m.status === 'QUEUED' && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Spin size="small" />{m.answer}
                  </div>
                )}
                {m.error && <p style={{ color: '#b91c1c', margin: 0 }}>{m.error}</p>}
                {m.answer && m.status !== 'QUEUED' && (
                  <>
                    <p style={{ margin: '0 0 8px', lineHeight: 1.7 }}>{m.answer}</p>
                    {m.citations && m.citations.length > 0 && (
                      <div>
                        <div style={{ fontSize: 11, color: '#667085', marginBottom: 4, fontWeight: 600 }}>
                          📎 引用来源 ({m.citations.length})
                        </div>
                        {m.citations.map((c, ci) => {
                          const displayName = c.resourceName
                            ?? m.resourceNames?.[c.resourceId]
                            ?? `${c.resourceType}/${c.resourceId.substring(0, 8)}`;
                          return (
                          <div key={c.id} style={{
                            fontSize: 12, padding: '6px 10px', marginBottom: 4,
                            background: '#fff', borderRadius: 6, border: '1px solid #e6eaf0',
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                          }}>
                            <span>
                              <Tag size="small" style={{ marginRight: 6 }}>[{ci + 1}]</Tag>
                              {displayName} — {locatorLabel(c.locatorJson)}
                            </span>
                            <span
                              style={{
                                color: '#2f6fed', cursor: 'pointer', fontSize: 11, fontWeight: 600,
                              }}
                              onClick={() => handleViewCitation(c)}
                              title={`查看 ${displayName}`}
                            >
                              查看
                            </span>
                          </div>
                          );
                        })}
                      </div>
                    )}
                    {m.answerId && (
                      <Button
                        size="small" theme="borderless" icon={<IconFile />}
                        style={{ marginTop: 8, fontSize: 11 }}
                        onClick={() => handleSaveDoc(m.answerId!, m.question ?? 'QA 结果')}
                      >
                        保存为文档
                      </Button>
                    )}
                  </>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Input area */}
      <div style={{ display: 'flex', gap: 8, paddingTop: 8, borderTop: '1px solid #e6eaf0' }}>
        <TextArea
          autosize rows={2}
          value={question}
          onChange={setQuestion}
          onEnterPress={e => { if (!e.shiftKey) { e.preventDefault(); send(); } }}
          placeholder={busy ? 'AI 正在检索知识库…' : '基于知识库提问，例如：新规对测试窗口有什么影响？'}
          disabled={busy}
        />
        <Button theme="solid" icon={<IconSend />} loading={busy} onClick={send} disabled={busy} />
      </div>
    </div>
  );
}
