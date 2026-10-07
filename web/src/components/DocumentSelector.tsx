// components/DocumentSelector.tsx
import { Select } from "antd";
import { useQuery } from "@tanstack/react-query";
import { fetchDocuments } from "../api/documents";

interface DocumentSelectorProps {
  value: string[];
  onChange: (docIds: string[]) => void;
}

export default function DocumentSelector({ value, onChange }: DocumentSelectorProps) {
  const { data: documents } = useQuery({
    queryKey: ["documents"],
    queryFn: fetchDocuments,
  });

  const readyDocs = (documents ?? []).filter((d) => d.status === "READY");

  return (
    <Select
      mode="multiple"
      allowClear
      optionFilterProp="label" 
      placeholder="请选择文档"
      value={value}
      onChange={onChange}
      style={{ width: "100%", marginBottom: 8 }}
      maxTagCount={2}
      options={readyDocs.map((d) => ({ label: d.fileName, value: d.docId }))}
      notFoundContent={readyDocs.length === 0 ? "暂无已就绪的文档" : undefined}
    />
  );
}