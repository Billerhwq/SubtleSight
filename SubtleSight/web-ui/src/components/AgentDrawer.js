import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { Button, SideSheet, TextArea, Toast } from '@douyinfe/semi-ui';
import { IconSend } from '@douyinfe/semi-icons';
import { post } from '../api/client';
import { useUi } from '../store/ui';
export function AgentDrawer() { const { agentOpen, setAgentOpen } = useUi(); const [text, setText] = useState(''), [busy, setBusy] = useState(false), [messages, setMessages] = useState([{ role: 'agent', text: '告诉我你想追踪什么、核实什么，或要建立怎样的发现视图。' }]); const send = async () => { if (!text.trim())
    return; const current = text; setMessages(v => [...v, { role: 'user', text: current }]); setText(''); setBusy(true); try {
    const result = await post('/agent', { message: current, confirmed: false, context: {} });
    setMessages(v => [...v, { role: 'agent', text: result.message, tools: result.tools }]);
}
catch (e) {
    Toast.error(e.message);
}
finally {
    setBusy(false);
} }; return _jsxs(SideSheet, { title: "\u95EE SubtleSight", visible: agentOpen, onCancel: () => setAgentOpen(false), width: 480, footer: _jsxs("div", { className: "agent-compose", children: [_jsx(TextArea, { autosize: true, rows: 2, value: text, onChange: setText, onEnterPress: e => { if (!e.shiftKey) {
                    e.preventDefault();
                    send();
                } }, placeholder: "\u641C\u7D22\u3001\u7814\u7A76\u3001\u521B\u5EFA\u89C6\u56FE\u6216\u8DDF\u8E2A\u76EE\u6807\u2026" }), _jsx(Button, { theme: "solid", icon: _jsx(IconSend, {}), loading: busy, onClick: send })] }), children: [_jsxs("div", { className: "agent-intro", children: [_jsx("span", { className: "brand-mark", children: "S" }), _jsxs("div", { children: [_jsx("strong", { children: "\u53D7\u63A7\u60C5\u62A5 Agent" }), _jsx("p", { children: "\u6240\u6709\u64CD\u4F5C\u901A\u8FC7\u540C\u4E00\u4E1A\u52A1\u670D\u52A1\uFF1B\u53D1\u5E03\u7B49\u9AD8\u98CE\u9669\u52A8\u4F5C\u5FC5\u987B\u786E\u8BA4\u3002" })] })] }), _jsx("div", { className: "chat-list", children: messages.map((m, i) => _jsxs("div", { className: `chat ${m.role}`, children: [_jsx("p", { children: m.text }), m.tools?.map(t => _jsx("code", { children: t }, t))] }, i)) })] }); }
export default AgentDrawer;
