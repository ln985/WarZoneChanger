<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

require_once __DIR__ . '/../config.php';

$response = ['success' => false, 'message' => '未知错误'];

try {
    $input = json_decode(file_get_contents('php://input'), true);
    
    if (!$input) {
        throw new Exception('无效的请求数据');
    }
    
    $cardKey = $input['cardKey'] ?? '';
    $deviceId = $input['deviceId'] ?? '';
    $deviceName = $input['deviceName'] ?? '未知设备';
    
    if (empty($cardKey)) {
        throw new Exception('卡密不能为空');
    }
    
    if (empty($deviceId)) {
        throw new Exception('设备标识不能为空');
    }
    
    // 读取卡密数据
    $cardsFile = DATA_DIR . '/cards.json';
    $cards = [];
    if (file_exists($cardsFile)) {
        $cards = json_decode(file_get_contents($cardsFile), true) ?: [];
    }
    
    // 查找卡密
    $cardIndex = -1;
    foreach ($cards as $index => $card) {
        if ($card['key'] === $cardKey) {
            $cardIndex = $index;
            break;
        }
    }
    
    if ($cardIndex === -1) {
        throw new Exception('卡密无效');
    }
    
    $card = $cards[$cardIndex];
    
    // 检查卡密是否已过期
    if (isset($card['expireTime']) && $card['expireTime'] > 0 && $card['expireTime'] < time()) {
        throw new Exception('卡密已过期');
    }
    
    // 检查卡密是否已被使用
    if (isset($card['used']) && $card['used'] && isset($card['deviceId']) && $card['deviceId'] !== $deviceId) {
        throw new Exception('卡密已被其他设备使用');
    }
    
    // 绑定设备
    $cards[$cardIndex]['used'] = true;
    $cards[$cardIndex]['deviceId'] = $deviceId;
    $cards[$cardIndex]['deviceName'] = $deviceName;
    $cards[$cardIndex]['lastUseTime'] = time();
    
    if (!isset($card['firstUseTime'])) {
        $cards[$cardIndex]['firstUseTime'] = time();
    }
    
    // 保存更新
    file_put_contents($cardsFile, json_encode($cards, JSON_PRETTY_PRINT));
    
    // 记录日志
    logAction('verify', $cardKey, $deviceId, $deviceName);
    
    // 计算剩余时间
    $remainingDays = -1;
    if (isset($card['expireTime']) && $card['expireTime'] > 0) {
        $remainingDays = max(0, floor(($card['expireTime'] - time()) / 86400));
    }
    
    $response = [
        'success' => true,
        'message' => '验证成功',
        'data' => [
            'cardKey' => $cardKey,
            'expireTime' => $card['expireTime'] ?? 0,
            'remainingDays' => $remainingDays,
            'maxDevices' => $card['maxDevices'] ?? 1,
            'features' => $card['features'] ?? ['basic']
        ]
    ];
    
} catch (Exception $e) {
    $response['message'] = $e->getMessage();
}

echo json_encode($response, JSON_UNESCAPED_UNICODE);