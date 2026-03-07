import React, { useEffect, useState } from 'react';
import { Card, List, Image, Empty, Spin, Button, message, Pagination } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { getImageHistory } from '../../api/image';
import './index.css';

interface HistoryItem {
  id: string;
  imageUrl: string;
  prompt: string;
  style: string;
  createdAt: string;
}

const History: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<HistoryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const pageSize = 12;

  const fetchHistory = async (page: number) => {
    setLoading(true);
    try {
      const response: any = await getImageHistory(page, pageSize);
      if (response.code === 200) {
        setData(response.data.list || []);
        setTotal(response.data.total || 0);
      }
    } catch (error) {
      message.error('获取历史记录失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory(current);
  }, [current]);

  const handleDownload = (imageUrl: string) => {
    const link = document.createElement('a');
    link.href = imageUrl;
    link.download = `pixel-ai-${Date.now()}.png`;
    link.click();
  };

  const handlePageChange = (page: number) => {
    setCurrent(page);
  };

  return (
    <div className="history-container">
      <Card title="生成历史" className="history-card">
        <Spin spinning={loading}>
          {data.length > 0 ? (
            <>
              <List
                grid={{
                  gutter: 16,
                  xs: 1,
                  sm: 2,
                  md: 3,
                  lg: 4,
                  xl: 4,
                  xxl: 6,
                }}
                dataSource={data}
                renderItem={(item) => (
                  <List.Item>
                    <Card
                      hoverable
                      className="history-item"
                      cover={
                        <Image
                          src={item.imageUrl}
                          alt={item.prompt}
                          className="history-image"
                        />
                      }
                      actions={[
                        <Button
                          type="link"
                          icon={<DownloadOutlined />}
                          onClick={() => handleDownload(item.imageUrl)}
                        >
                          下载
                        </Button>,
                      ]}
                    >
                      <Card.Meta
                        description={
                          <div className="item-info">
                            <p className="prompt" title={item.prompt}>
                              {item.prompt}
                            </p>
                            <p className="time">{item.createdAt}</p>
                          </div>
                        }
                      />
                    </Card>
                  </List.Item>
                )}
              />
              <div className="pagination-wrapper">
                <Pagination
                  current={current}
                  total={total}
                  pageSize={pageSize}
                  onChange={handlePageChange}
                  showTotal={(total) => `共 ${total} 条记录`}
                />
              </div>
            </>
          ) : (
            <Empty description="暂无生成记录" />
          )}
        </Spin>
      </Card>
    </div>
  );
};

export default History;
