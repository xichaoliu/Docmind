// pages/DocumentsPage.tsx
import { Table, Tag, Button, Upload, Space, message } from "antd";
import { UploadOutlined, DeleteOutlined } from "@ant-design/icons";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchDocuments, uploadDocument, deleteDocument, type DocumentItem } from "../../api/documents";

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  PROCESSING: { color: "processing", label: "处理中" },
  READY: { color: "success", label: "已就绪" },
  FAILED: { color: "error", label: "失败" },
};

export default function DocumentsPage() {
  const queryClient = useQueryClient();

  const { data: documents, isLoading } = useQuery({
    queryKey: ["documents"],
    queryFn: fetchDocuments,
    refetchInterval: (query) => {
      // 只要列表里还有 PROCESSING 状态的文档，就每 3 秒轮询一次；全部结束就停止轮询
      const hasProcessing = query.state.data?.some((d) => d.status === "PROCESSING");
      return hasProcessing ? 3000 : false;
    },
  });

  const { mutate: upload, isPending: isUploading } = useMutation({
    mutationFn: uploadDocument,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      message.success("上传成功，正在处理");
    },
    onError: () => message.error("上传失败"),
  });

  const { mutate: remove } = useMutation({
    mutationFn: deleteDocument,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      message.success("已删除");
    },
    onError: () => message.error("删除失败"),
  });

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: "flex", justifyContent: "space-between" }}>
        <h3 style={{ margin: 0 }}>文档管理</h3>
        <Upload
          showUploadList={false}
          beforeUpload={(file) => {
            upload(file);
            console.log("uploading file:", file);
            return false;   // 阻止 antd 自动上传，交给 mutation 处理
          }}
        >
          <Button type="primary" icon={<UploadOutlined />} loading={isUploading}>
            上传文档
          </Button>
        </Upload>
      </div>

      <Table<DocumentItem>
        rowKey="docId"
        loading={isLoading}
        dataSource={documents ?? []}
        columns={[
          { title: "文件名", dataIndex: "fileName" },
          {
            title: "状态",
            dataIndex: "status",
            render: (status: string) => (
              <Tag color={STATUS_MAP[status]?.color}>{STATUS_MAP[status]?.label ?? status}</Tag>
            ),
          },
          { 
            title: "上传时间", 
            dataIndex: "createdAt",
            render: (_, record) => <span>{new Date(record.createdAt).toLocaleString()}</span>
         },
          {
            title: "操作",
            render: (_, record) => (
              <Space>
                <Button
                  danger
                  type="text"
                  icon={<DeleteOutlined />}
                  onClick={() => remove(record.id)}
                >
                  删除
                </Button>
              </Space>
            ),
          },
        ]}
      />
    </div>
  );
}