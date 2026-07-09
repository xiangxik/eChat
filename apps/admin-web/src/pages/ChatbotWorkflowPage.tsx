import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
} from 'antd';
import { useEffect, useState } from 'react';
import type { DragEvent as ReactDragEvent, PointerEvent as ReactPointerEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import {
  adminApi,
  type ChatMessage,
  type ChatbotWorkflow,
  type ChatbotWorkflowNode,
  type ChatbotWorkflowTransition,
  type ChatbotWorkflowValidationResult,
} from '../api/admin';
import { formatDate } from './pageUtils';
import { ErrorAlert, PageSectionHeader } from './shared';

type NodeFormValues = Omit<ChatbotWorkflowNode, 'id' | 'metadata' | 'createdAt' | 'updatedAt'>;
type TransitionFormValues = Omit<ChatbotWorkflowTransition, 'id' | 'metadata' | 'createdAt' | 'updatedAt'>;
type NodePosition = { x: number; y: number };
type DragState = { nodeKey: string; startX: number; startY: number; originX: number; originY: number };
type TransitionDragState = { fromNodeKey: string; startX: number; startY: number; currentX: number; currentY: number; surfaceLeft: number; surfaceTop: number };

const NODE_WIDTH = 240;
const NODE_HEIGHT = 118;
const START_NODE_KEY = 'Start';

export function ChatbotWorkflowPage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const params = useParams();
  const queryClient = useQueryClient();
  const chatbotId = Number(params.chatbotId);
  const [nodes, setNodes] = useState<ChatbotWorkflowNode[]>([]);
  const [transitions, setTransitions] = useState<ChatbotWorkflowTransition[]>([]);
  const [validationResult, setValidationResult] = useState<ChatbotWorkflowValidationResult>();
  const [editingNodeKey, setEditingNodeKey] = useState<string>();
  const [editingTransitionIndex, setEditingTransitionIndex] = useState<number>();
  const [nodeDrawerOpen, setNodeDrawerOpen] = useState(false);
  const [transitionDrawerOpen, setTransitionDrawerOpen] = useState(false);
  const [dragging, setDragging] = useState<DragState>();
  const [transitionDragging, setTransitionDragging] = useState<TransitionDragState>();
  const [debugConversationId, setDebugConversationId] = useState<number>();
  const [debugCurrentNodeKey, setDebugCurrentNodeKey] = useState<string>();
  const [debugMessages, setDebugMessages] = useState<ChatMessage[]>([]);
  const [debugInput, setDebugInput] = useState('');
  const [nodeForm] = Form.useForm<NodeFormValues>();
  const [transitionForm] = Form.useForm<TransitionFormValues>();

  const chatbotQuery = useQuery({ queryKey: ['chatbots', chatbotId], queryFn: () => adminApi.getChatbot(chatbotId), enabled: Number.isFinite(chatbotId) });
  const workflowQuery = useQuery({ queryKey: ['chatbot-workflow', chatbotId], queryFn: () => adminApi.getChatbotWorkflow(chatbotId), enabled: Number.isFinite(chatbotId) });
  const modelsQuery = useQuery({ queryKey: ['models'], queryFn: adminApi.listModels });

  useEffect(() => {
    if (!dragging) {
      return;
    }
    const handleMove = (event: PointerEvent) => {
      setNodes((current) =>
        current.map((node) =>
          node.nodeKey === dragging.nodeKey
            ? withNodePosition(node, {
                x: Math.max(24, dragging.originX + event.clientX - dragging.startX),
                y: Math.max(24, dragging.originY + event.clientY - dragging.startY),
              })
            : node,
        ),
      );
    };
    const handleUp = () => setDragging(undefined);
    window.addEventListener('pointermove', handleMove);
    window.addEventListener('pointerup', handleUp);
    return () => {
      window.removeEventListener('pointermove', handleMove);
      window.removeEventListener('pointerup', handleUp);
    };
  }, [dragging]);

  useEffect(() => {
    if (!transitionDragging) {
      return;
    }
    const handleMove = (event: PointerEvent) => {
      setTransitionDragging((current) => current ? { ...current, currentX: event.clientX - current.surfaceLeft, currentY: event.clientY - current.surfaceTop } : undefined);
    };
    const handleUp = (event: PointerEvent) => {
      const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-workflow-node-key]');
      const targetNodeKey = target?.dataset.workflowNodeKey;
      if (targetNodeKey && targetNodeKey !== transitionDragging.fromNodeKey) {
        addTransition(transitionDragging.fromNodeKey, targetNodeKey);
      }
      setTransitionDragging(undefined);
    };
    window.addEventListener('pointermove', handleMove);
    window.addEventListener('pointerup', handleUp);
    return () => {
      window.removeEventListener('pointermove', handleMove);
      window.removeEventListener('pointerup', handleUp);
    };
  }, [transitionDragging, transitions]);

  useEffect(() => {
    if (workflowQuery.data) {
      setNodes(workflowQuery.data.nodes.map((node, index) => ensureNodePosition(node, index)));
      setTransitions(workflowQuery.data.transitions);
      setValidationResult(undefined);
    }
  }, [workflowQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () => adminApi.saveChatbotWorkflow(chatbotId, normalizeWorkflow({ chatbotId, nodes, transitions })),
    onSuccess: (workflow) => {
      message.success('Workflow saved');
      setNodes(workflow.nodes);
      setTransitions(workflow.transitions);
      setValidationResult(undefined);
      void queryClient.invalidateQueries({ queryKey: ['chatbot-workflow', chatbotId] });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const validateMutation = useMutation({
    mutationFn: () => adminApi.validateChatbotWorkflow(chatbotId, normalizeWorkflow({ chatbotId, nodes, transitions })),
    onSuccess: (result) => {
      setValidationResult(result);
      if (result.valid) {
        message.success('Workflow is valid');
      }
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Validation failed'),
  });

  const createDebugConversationMutation = useMutation({
    mutationFn: () => adminApi.createChatConversation({
      chatbotId,
      anonymousSessionId: `workflow-debug-${Date.now()}`,
      title: `Workflow debug ${formatDate(new Date())}`,
    }),
    onSuccess: (conversation) => {
      setDebugConversationId(conversation.id);
      setDebugCurrentNodeKey(conversation.currentWorkflowNodeKey ?? undefined);
      setDebugMessages([conversation.userMessage, conversation.assistantMessage].filter((item): item is ChatMessage => Boolean(item)));
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Unable to start debug chat'),
  });

  const sendDebugMessageMutation = useMutation({
    mutationFn: async (content: string) => {
      const conversationId = debugConversationId ?? (await adminApi.createChatConversation({
        chatbotId,
        anonymousSessionId: `workflow-debug-${Date.now()}`,
        title: `Workflow debug ${formatDate(new Date())}`,
      })).id;
      setDebugConversationId(conversationId);
      return adminApi.sendChatMessage(conversationId, { message: content, metadata: { source: 'admin-workflow-debug' } });
    },
    onSuccess: (response) => {
      setDebugCurrentNodeKey(response.conversation.currentWorkflowNodeKey ?? undefined);
      setDebugMessages((current) => [...current, response.userMessage, response.assistantMessage]);
      setDebugInput('');
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Debug message failed'),
  });

  const modelOptions = (modelsQuery.data ?? [])
    .filter((model) => model.enabled && model.modelType === 'CHAT')
    .map((model) => ({ label: `${model.displayName} (${model.providerName})`, value: model.id }));
  const nodeOptions = nodes.map((node) => ({ label: `${node.name} (${node.nodeKey})`, value: node.nodeKey }));
  const positionedNodes = nodes.map((node, index) => ensureNodePosition(node, index));
  const nodeByKey = new Map(positionedNodes.map((node) => [node.nodeKey, node]));
  const canvasWidth = Math.max(920, ...positionedNodes.map((node) => getNodePosition(node, 0).x + NODE_WIDTH + 160));
  const canvasHeight = Math.max(520, ...positionedNodes.map((node) => getNodePosition(node, 0).y + NODE_HEIGHT + 120));

  const openCreateNode = () => {
    const nodeKey = nextNodeKey(nodes);
    setEditingNodeKey(undefined);
    nodeForm.resetFields();
    nodeForm.setFieldsValue({
      nodeKey,
      name: nodeKey,
      dslContent: defaultNodeDsl(nodeKey),
      version: 1,
      modelId: modelOptions[0]?.value ?? null,
      enabled: true,
      start: false,
    } as NodeFormValues);
    setNodeDrawerOpen(true);
  };

  const openEditNode = (node: ChatbotWorkflowNode) => {
    setEditingNodeKey(node.nodeKey);
    nodeForm.resetFields();
    nodeForm.setFieldsValue(toNodeFormValues(node));
    setNodeDrawerOpen(true);
  };

  const saveNode = (values: NodeFormValues) => {
    const previousNode = nodes.find((node) => node.nodeKey === editingNodeKey || node.nodeKey === values.nodeKey);
    const isEditingStart = editingNodeKey === START_NODE_KEY;
    const nextNode = normalizeNode({
      ...values,
      nodeKey: isEditingStart ? START_NODE_KEY : values.nodeKey,
      name: values.name,
      modelId: isEditingStart ? null : values.modelId,
      enabled: isEditingStart ? true : values.enabled,
      start: isEditingStart ? true : values.start,
      metadata: previousNode?.metadata ?? defaultNodeMetadata(nodes.length),
    });
    setNodes((current) => {
      const withoutCurrent = current.filter((node) => node.nodeKey !== editingNodeKey && node.nodeKey !== nextNode.nodeKey);
      const nextNodes = [...withoutCurrent, nextNode].sort((left, right) => left.nodeKey.localeCompare(right.nodeKey));
      return nextNode.start ? nextNodes.map((node) => ({ ...node, start: node.nodeKey === nextNode.nodeKey })) : nextNodes;
    });
    if (editingNodeKey && editingNodeKey !== nextNode.nodeKey) {
      setTransitions((current) =>
        current.map((transition) => ({
          ...transition,
          fromNodeKey: transition.fromNodeKey === editingNodeKey ? nextNode.nodeKey : transition.fromNodeKey,
          toNodeKey: transition.toNodeKey === editingNodeKey ? nextNode.nodeKey : transition.toNodeKey,
        })),
      );
    }
    setNodeDrawerOpen(false);
    setValidationResult(undefined);
  };

  const startNodeDrag = (event: ReactPointerEvent, node: ChatbotWorkflowNode) => {
    event.preventDefault();
    const position = getNodePosition(node, 0);
    setDragging({ nodeKey: node.nodeKey, startX: event.clientX, startY: event.clientY, originX: position.x, originY: position.y });
    setValidationResult(undefined);
  };

  const createNodeAt = (position: NodePosition) => {
    const modelId = modelOptions[0]?.value;
    if (!modelId) {
      message.warning('Create an enabled chat model before adding workflow nodes');
      return;
    }
    const nodeKey = nextNodeKey(nodes);
    const nextNode = normalizeNode({
      nodeKey,
      name: nodeKey,
      dslContent: defaultNodeDsl(nodeKey),
      version: 1,
      modelId,
      enabled: true,
      start: false,
      metadata: position,
    });
    setNodes((current) => [...current, nextNode].sort((left, right) => left.nodeKey.localeCompare(right.nodeKey)));
    setValidationResult(undefined);
  };

  const addTransition = (fromNodeKey: string, toNodeKey: string) => {
    const priority = nextTransitionPriority(transitions, fromNodeKey);
    const nextTransition = normalizeTransition({
      name: `${fromNodeKey} -> ${toNodeKey}`,
      fromNodeKey,
      toNodeKey,
      priority,
      enabled: true,
      conditionExpression: 'true',
    });
    setTransitions((current) => [...current, nextTransition].sort(compareTransitions));
    setValidationResult(undefined);
  };

  const startTransitionDrag = (event: ReactPointerEvent, node: ChatbotWorkflowNode) => {
    event.preventDefault();
    event.stopPropagation();
    const position = getNodePosition(node, 0);
    const surface = event.currentTarget.closest('.workflow-canvas-surface')?.getBoundingClientRect();
    const startX = position.x + NODE_WIDTH;
    const startY = position.y + NODE_HEIGHT / 2;
    setTransitionDragging({
      fromNodeKey: node.nodeKey,
      startX,
      startY,
      currentX: event.clientX - (surface?.left ?? 0),
      currentY: event.clientY - (surface?.top ?? 0),
      surfaceLeft: surface?.left ?? 0,
      surfaceTop: surface?.top ?? 0,
    });
  };

  const handleCanvasDrop = (event: ReactDragEvent<HTMLDivElement>) => {
    const tool = event.dataTransfer.getData('application/x-echat-workflow-tool');
    if (tool !== 'node') {
      return;
    }
    event.preventDefault();
    const surface = event.currentTarget.getBoundingClientRect();
    createNodeAt({ x: Math.max(24, event.clientX - surface.left), y: Math.max(24, event.clientY - surface.top) });
  };

  const handleToolDragStart = (event: ReactDragEvent<HTMLElement>, tool: 'node') => {
    event.dataTransfer.setData('application/x-echat-workflow-tool', tool);
    event.dataTransfer.effectAllowed = 'copy';
  };

  const deleteNode = (nodeKey: string) => {
    if (nodeKey === START_NODE_KEY) {
      message.warning('Start node cannot be deleted');
      return;
    }
    setNodes((current) => current.filter((node) => node.nodeKey !== nodeKey));
    setTransitions((current) => current.filter((transition) => transition.fromNodeKey !== nodeKey && transition.toNodeKey !== nodeKey));
    setValidationResult(undefined);
  };

  const openCreateTransition = () => {
    setEditingTransitionIndex(undefined);
    transitionForm.resetFields();
    transitionForm.setFieldsValue({ enabled: true, priority: 0, conditionExpression: 'true' } as TransitionFormValues);
    setTransitionDrawerOpen(true);
  };

  const openEditTransition = (transition: ChatbotWorkflowTransition, index: number) => {
    setEditingTransitionIndex(index);
    transitionForm.resetFields();
    transitionForm.setFieldsValue(toTransitionFormValues(transition));
    setTransitionDrawerOpen(true);
  };

  const saveTransition = (values: TransitionFormValues) => {
    const nextTransition = normalizeTransition(values);
    setTransitions((current) => {
      if (editingTransitionIndex === undefined) {
        return [...current, nextTransition].sort(compareTransitions);
      }
      return current.map((transition, index) => (index === editingTransitionIndex ? nextTransition : transition)).sort(compareTransitions);
    });
    setTransitionDrawerOpen(false);
    setValidationResult(undefined);
  };

  const deleteTransition = (indexToDelete: number) => {
    setTransitions((current) => current.filter((_, index) => index !== indexToDelete));
    setValidationResult(undefined);
  };

  const resetDebugChat = () => {
    setDebugConversationId(undefined);
    setDebugCurrentNodeKey(undefined);
    setDebugMessages([]);
    createDebugConversationMutation.mutate();
  };

  const sendDebugMessage = () => {
    const content = debugInput.trim();
    if (!content) {
      return;
    }
    sendDebugMessageMutation.mutate(content);
  };

  return (
    <div className="page-stack">
      <ErrorAlert error={chatbotQuery.error ?? workflowQuery.error ?? modelsQuery.error} />
      {validationResult && !validationResult.valid && <Alert type="error" showIcon message="Workflow validation failed" description={validationResult.errors.join('\n')} />}
      {validationResult?.valid && validationResult.warnings.length > 0 && <Alert type="warning" showIcon message="Workflow warnings" description={validationResult.warnings.join('\n')} />}
      <Card className="admin-data-card">
        <PageSectionHeader
          title={`Workflow${chatbotQuery.data ? `: ${chatbotQuery.data.name}` : ''}`}
          actions={
            <Space>
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/chatbots')}>
                Chatbots
              </Button>
              <Button loading={validateMutation.isPending} onClick={() => validateMutation.mutate()}>
                Validate
              </Button>
              <Button type="primary" loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
                Save
              </Button>
            </Space>
          }
        />
        <div className="workflow-workspace">
          <div className="workflow-canvas-card">
            <div className="workflow-toolbar">
              <Space>
                <Button size="small" icon={<PlusOutlined />} draggable onDragStart={(event) => handleToolDragStart(event, 'node')} onClick={openCreateNode}>
                  Node
                </Button>
                <Button size="small" icon={<PlusOutlined />} onClick={openCreateTransition} disabled={nodes.length < 2}>
                  Transition
                </Button>
              </Space>
              <Space size={6} className="workflow-counts">
                <Tag>{nodes.length} nodes</Tag>
                <Tag>{transitions.length} transitions</Tag>
              </Space>
            </div>
            <div className="workflow-canvas-viewport">
              <div className="workflow-canvas-surface" style={{ width: canvasWidth, height: canvasHeight }} onDragOver={(event) => event.preventDefault()} onDrop={handleCanvasDrop}>
                <svg className="workflow-canvas-edges" width={canvasWidth} height={canvasHeight} viewBox={`0 0 ${canvasWidth} ${canvasHeight}`}>
                  <defs>
                    <marker id="workflow-arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
                      <path d="M0,0 L0,6 L9,3 z" fill="#1677a3" />
                    </marker>
                    <marker id="workflow-arrow-disabled" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
                      <path d="M0,0 L0,6 L9,3 z" fill="#94a3b8" />
                    </marker>
                  </defs>
                  {transitions.map((transition, index) => {
                    const fromNode = nodeByKey.get(transition.fromNodeKey);
                    const toNode = nodeByKey.get(transition.toNodeKey);
                    if (!fromNode || !toNode) {
                      return null;
                    }
                    const fromPosition = getNodePosition(fromNode, 0);
                    const toPosition = getNodePosition(toNode, 0);
                    const startX = fromPosition.x + NODE_WIDTH;
                    const startY = fromPosition.y + NODE_HEIGHT / 2;
                    const endX = toPosition.x;
                    const endY = toPosition.y + NODE_HEIGHT / 2;
                    const controlOffset = Math.max(90, Math.abs(endX - startX) / 2);
                    const path = `M ${startX} ${startY} C ${startX + controlOffset} ${startY}, ${endX - controlOffset} ${endY}, ${endX} ${endY}`;
                    const labelX = (startX + endX) / 2;
                    const labelY = (startY + endY) / 2 - 8;
                    return (
                      <g key={`${transition.fromNodeKey}-${transition.toNodeKey}-${index}`} className="workflow-edge" onDoubleClick={() => openEditTransition(transition, index)}>
                        <path className={transition.enabled ? 'workflow-edge-path' : 'workflow-edge-path is-disabled'} d={path} markerEnd={transition.enabled ? 'url(#workflow-arrow)' : 'url(#workflow-arrow-disabled)'} />
                        <text className="workflow-edge-label" x={labelX} y={labelY} textAnchor="middle">
                          {transition.priority}: {transition.name}
                        </text>
                      </g>
                    );
                  })}
                  {transitionDragging && (
                    <path
                      className="workflow-edge-path is-draft"
                      d={`M ${transitionDragging.startX} ${transitionDragging.startY} L ${transitionDragging.currentX} ${transitionDragging.currentY}`}
                    />
                  )}
                </svg>
                {positionedNodes.map((node, index) => {
                  const position = getNodePosition(node, index);
                  const modelName = node.modelId ? modelsQuery.data?.find((model) => model.id === node.modelId)?.displayName ?? `Model #${node.modelId}` : 'System reply';
                  const isStartNode = node.nodeKey === START_NODE_KEY;
                  return (
                    <div
                      key={node.nodeKey}
                      data-workflow-node-key={node.nodeKey}
                      className={`workflow-node-card${node.start ? ' is-start' : ''}${node.enabled ? '' : ' is-disabled'}${dragging?.nodeKey === node.nodeKey ? ' is-dragging' : ''}`}
                      style={{ transform: `translate(${position.x}px, ${position.y}px)` }}
                      onPointerDown={(event) => startNodeDrag(event, node)}
                    >
                      <div className="workflow-node-head">
                        <span className="workflow-node-title">{node.name}</span>
                        <Space size={4} onPointerDown={(event) => event.stopPropagation()}>
                          {node.start && <Tag color="blue">Start</Tag>}
                          {isStartNode && <Tag color="green">Locked</Tag>}
                          {!node.enabled && <Tag>Disabled</Tag>}
                          <Tooltip title="Edit node">
                            <Button size="small" type="text" icon={<EditOutlined />} onClick={() => openEditNode(node)} />
                          </Tooltip>
                          {!isStartNode && (
                            <Popconfirm title="Delete this node?" onConfirm={() => deleteNode(node.nodeKey)}>
                              <Tooltip title="Delete node">
                                <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                              </Tooltip>
                            </Popconfirm>
                          )}
                        </Space>
                      </div>
                      <div className="workflow-node-key">{node.nodeKey}</div>
                      <div className="workflow-node-policy">{modelName}</div>
                      <Tooltip title="Drag to another node">
                        <button className="workflow-node-connector" type="button" onPointerDown={(event) => startTransitionDrag(event, node)} />
                      </Tooltip>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
          <div className="workflow-inspector">
            <Card size="small" title="Chatbot" extra={<Button size="small" onClick={resetDebugChat} loading={createDebugConversationMutation.isPending}>New</Button>}>
              <div className="workflow-debug-panel">
                <Space size={6} wrap>
                  <Tag>{debugConversationId ? `#${debugConversationId}` : 'No session'}</Tag>
                  {debugCurrentNodeKey && <Tag color="blue">{debugCurrentNodeKey}</Tag>}
                </Space>
                <div className="workflow-debug-messages">
                  {debugMessages.length === 0 && <div className="workflow-empty">Save changes, then send a message.</div>}
                  {debugMessages.map((item) => (
                    <div key={item.id} className={`workflow-debug-message ${item.role === 'USER' ? 'is-user' : 'is-assistant'}`}>
                      <span>{item.role}</span>
                      <p>{item.content}</p>
                    </div>
                  ))}
                </div>
                <Input.TextArea
                  rows={3}
                  value={debugInput}
                  onChange={(event) => setDebugInput(event.target.value)}
                  onPressEnter={(event) => {
                    if (!event.shiftKey) {
                      event.preventDefault();
                      sendDebugMessage();
                    }
                  }}
                  placeholder="Message"
                />
                <Button type="primary" block loading={sendDebugMessageMutation.isPending} onClick={sendDebugMessage}>
                  Send
                </Button>
              </div>
            </Card>
          </div>
        </div>
      </Card>

      <Drawer
        title={editingNodeKey ? 'Edit Node' : 'New Node'}
        open={nodeDrawerOpen}
        width={520}
        destroyOnClose
        onClose={() => setNodeDrawerOpen(false)}
        extra={<Button type="primary" onClick={() => nodeForm.submit()}>Apply</Button>}
      >
        <Form form={nodeForm} layout="vertical" onFinish={saveNode} initialValues={{ enabled: true, start: false }}>
          {editingNodeKey === START_NODE_KEY && <Alert type="info" showIcon message="Start is built in" description="Node Key is fixed and the node cannot be deleted." style={{ marginBottom: 12 }} />}
          <Form.Item name="nodeKey" label="Node Key" rules={[{ required: true, max: 120, pattern: /^[A-Za-z0-9_.-]+$/ }]}>
            <Input placeholder="intake" disabled={editingNodeKey === START_NODE_KEY} />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="Intake" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="modelId" label="Model" rules={editingNodeKey === START_NODE_KEY ? [] : [{ required: true }]}> 
            <Select showSearch allowClear optionFilterProp="label" options={modelOptions} disabled={editingNodeKey === START_NODE_KEY} />
          </Form.Item>
          <Form.Item name="version" label="Policy Version" rules={[{ required: true }]}> 
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="dslContent" label="Context Policy DSL" rules={[{ required: true }]}> 
            <Input.TextArea rows={12} spellCheck={false} />
          </Form.Item>
          <Form.Item name="start" label="Start Node" valuePropName="checked">
            <Switch disabled={editingNodeKey === START_NODE_KEY} />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch disabled={editingNodeKey === START_NODE_KEY} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={editingTransitionIndex === undefined ? 'New Transition' : 'Edit Transition'}
        open={transitionDrawerOpen}
        width={560}
        destroyOnClose
        onClose={() => setTransitionDrawerOpen(false)}
        extra={
          <Space>
            {editingTransitionIndex !== undefined && (
              <Popconfirm title="Delete this transition?" onConfirm={() => { deleteTransition(editingTransitionIndex); setTransitionDrawerOpen(false); }}>
                <Button danger>Delete</Button>
              </Popconfirm>
            )}
            <Button type="primary" onClick={() => transitionForm.submit()}>Apply</Button>
          </Space>
        }
      >
        <Form form={transitionForm} layout="vertical" onFinish={saveTransition} initialValues={{ enabled: true, priority: 0 }}>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="Route to billing" />
          </Form.Item>
          <Form.Item name="fromNodeKey" label="From Node" rules={[{ required: true }]}>
            <Select options={nodeOptions} />
          </Form.Item>
          <Form.Item name="toNodeKey" label="To Node" rules={[{ required: true }]}>
            <Select options={nodeOptions} />
          </Form.Item>
          <Form.Item name="priority" label="Priority" rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="conditionExpression" label="Condition Expression" rules={[{ required: true }]}>
            <Input.TextArea rows={4} placeholder="user.message contains 'billing'" />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

function normalizeWorkflow(workflow: ChatbotWorkflow): ChatbotWorkflow {
  return {
    chatbotId: workflow.chatbotId,
    nodes: workflow.nodes.map(normalizeNode),
    transitions: workflow.transitions.map(normalizeTransition),
  };
}

function normalizeNode(node: ChatbotWorkflowNode): ChatbotWorkflowNode {
  return {
    nodeKey: node.nodeKey.trim(),
    name: node.name.trim(),
    description: node.description?.trim() || undefined,
    dslContent: node.dslContent.trim(),
    version: node.version ?? 1,
    modelId: node.modelId ?? null,
    enabled: node.enabled !== false,
    start: node.start === true,
    metadata: node.metadata ?? {},
  };
}

function normalizeTransition(transition: ChatbotWorkflowTransition): ChatbotWorkflowTransition {
  return {
    name: transition.name.trim(),
    fromNodeKey: transition.fromNodeKey,
    toNodeKey: transition.toNodeKey,
    priority: transition.priority ?? 0,
    enabled: transition.enabled !== false,
    conditionExpression: transition.conditionExpression.trim(),
    metadata: transition.metadata ?? {},
  };
}

function compareTransitions(left: ChatbotWorkflowTransition, right: ChatbotWorkflowTransition) {
  const source = left.fromNodeKey.localeCompare(right.fromNodeKey);
  return source === 0 ? left.priority - right.priority : source;
}

function ensureNodePosition(node: ChatbotWorkflowNode, index: number): ChatbotWorkflowNode {
  return withNodePosition(node, getNodePosition(node, index));
}

function getNodePosition(node: ChatbotWorkflowNode, index: number): NodePosition {
  const metadata = node.metadata ?? {};
  const x = numeric(metadata.x);
  const y = numeric(metadata.y);
  return {
    x: x ?? 56 + (index % 3) * 310,
    y: y ?? 64 + Math.floor(index / 3) * 180,
  };
}

function withNodePosition(node: ChatbotWorkflowNode, position: NodePosition): ChatbotWorkflowNode {
  return {
    ...node,
    metadata: {
      ...(node.metadata ?? {}),
      x: Math.round(position.x),
      y: Math.round(position.y),
    },
  };
}

function defaultNodeMetadata(index: number): Record<string, unknown> {
  return getNodePosition({ nodeKey: '', name: '', dslContent: defaultNodeDsl('node'), version: 1, modelId: null, enabled: true, start: false }, index);
}

function defaultNodeDsl(nodeKey: string) {
  return `<contextPolicy name="${nodeKey}" maxTokens="12000">
  <system priority="100">You are a helpful assistant for ${nodeKey}.</system>
  <variables>
    <var name="conversation" source="conversation.messages" maxMessages="20" />
  </variables>
  <output>
    <section name="system" />
    <section name="conversation" />
  </output>
</contextPolicy>`;
}

function nextNodeKey(nodes: ChatbotWorkflowNode[]) {
  const keys = new Set(nodes.map((node) => node.nodeKey));
  let index = nodes.length + 1;
  let candidate = `node-${index}`;
  while (keys.has(candidate)) {
    index++;
    candidate = `node-${index}`;
  }
  return candidate;
}

function nextTransitionPriority(transitions: ChatbotWorkflowTransition[], fromNodeKey: string) {
  const sourcePriorities = transitions
    .filter((transition) => transition.fromNodeKey === fromNodeKey)
    .map((transition) => transition.priority ?? 0);
  return sourcePriorities.length === 0 ? 0 : Math.max(...sourcePriorities) + 1;
}

function numeric(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function toNodeFormValues(node: ChatbotWorkflowNode): NodeFormValues {
  return {
    nodeKey: node.nodeKey,
    name: node.name,
    description: node.description,
    dslContent: node.dslContent,
    version: node.version,
    modelId: node.modelId,
    enabled: node.enabled,
    start: node.start,
  };
}

function toTransitionFormValues(transition: ChatbotWorkflowTransition): TransitionFormValues {
  return {
    name: transition.name,
    fromNodeKey: transition.fromNodeKey,
    toNodeKey: transition.toNodeKey,
    priority: transition.priority,
    enabled: transition.enabled,
    conditionExpression: transition.conditionExpression,
  };
}