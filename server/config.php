<?php
/**
 * 服务端配置
 */
define('DATA_DIR', __DIR__ . '/data');

// 确保数据目录存在
if (!is_dir(DATA_DIR)) {
    mkdir(DATA_DIR, 0755, true);
}

/**
 * 记录操作日志
 */
function logAction($action, $cardKey, $deviceId, $deviceName = '') {
    $logsFile = DATA_DIR . '/logs.json';
    $logs = [];
    if (file_exists($logsFile)) {
        $logs = json_decode(file_get_contents($logsFile), true) ?: [];
    }
    
    $logs[] = [
        'time' => date('Y-m-d H:i:s'),
        'action' => $action,
        'cardKey' => substr($cardKey, 0, 8) . '****',
        'deviceId' => $deviceId,
        'deviceName' => $deviceName,
        'ip' => $_SERVER['REMOTE_ADDR'] ?? ''
    ];
    
    // 只保留最近1000条日志
    if (count($logs) > 1000) {
        $logs = array_slice($logs, -1000);
    }
    
    file_put_contents($logsFile, json_encode($logs, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
}