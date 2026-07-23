import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
export function PageHeader({ eyebrow, title, description, actions }) { return _jsxs("header", { className: "page-header", children: [_jsxs("div", { children: [_jsx("span", { className: "eyebrow", children: eyebrow }), _jsx("h1", { children: title }), _jsx("p", { children: description })] }), actions && _jsx("div", { className: "page-actions", children: actions })] }); }
export default PageHeader;
