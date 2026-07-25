import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import semiGlobal from '@douyinfe/semi-ui/lib/es/_utils/semi-global';
import '../node_modules/@douyinfe/semi-ui/dist/css/semi.min.css';
import './styles.css';
import './state.css';
import './knowledge.css';
import './knowledge-editor.css';
import './shell.css';
import App from './App';

// Inject createRoot for Semi UI React 19 compatibility
semiGlobal.config = { ...semiGlobal.config, createRoot: ReactDOM.createRoot };

const client=new QueryClient({defaultOptions:{queries:{staleTime:15_000,retry:1,refetchOnWindowFocus:false},mutations:{retry:0}}});
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={client}><App/></QueryClientProvider></React.StrictMode>);
