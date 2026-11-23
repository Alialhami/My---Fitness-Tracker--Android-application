<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
function respond(array $data, int $status = 200): never {
    http_response_code($status);
    echo json_encode($data, JSON_THROW_ON_ERROR);
    exit;
}
function fail(string $message, int $status = 422): never { respond(['error' => $message], $status); }
set_exception_handler(function(Throwable $e): void {
    error_log((string)$e);
    fail('The server could not complete this request. Please try again.', 500);
});
function db(): PDO {
    static $pdo;
    if (!$pdo) {
        $file = __DIR__ . '/config.local.php';
        if (!is_file($file)) fail('Server database configuration is missing.', 503);
        $c = require $file;
        $pdo = new PDO("mysql:host={$c['host']};port={$c['port']};dbname={$c['database']};charset=utf8mb4",
            $c['user'], $c['password'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false]);
        $pdo->exec("SET time_zone = '+00:00'");
    }
    return $pdo;
}
function input(): array {
    if ((int)($_SERVER['CONTENT_LENGTH'] ?? 0) > 16384) fail('Request too large.', 413);
    try { $data = json_decode(file_get_contents('php://input'), true, 32, JSON_THROW_ON_ERROR); }
    catch(JsonException $e) { fail('Send a valid JSON object.'); }
    if (!is_array($data)) fail('Send a JSON object.');
    return $data;
}
function textValue(array $d, string $key, int $max, bool $required=true): string {
    if (isset($d[$key]) && !is_string($d[$key])) fail("Invalid $key.");
    $v = trim($d[$key] ?? '');
    if (($required && $v === '') || strlen($v) > $max) fail("Please enter a valid $key (up to $max characters).");
    return $v;
}
function numberValue(array $d, string $key, float $min, float $max, bool $integer=false): float|int {
    $v = $d[$key] ?? null;
    if (!is_numeric($v) || !is_finite((float)$v) || $v < $min || $v > $max || ($integer && floor((float)$v) != $v))
        fail("$key must be " . ($integer ? 'a whole number ' : '') . "between $min and $max.");
    return $integer ? (int)$v : (float)$v;
}
function dateValue(array $d, string $key): string {
    $v=textValue($d,$key,10);
    $date=DateTimeImmutable::createFromFormat('!Y-m-d',$v);
    if (!$date || $date->format('Y-m-d') !== $v || $v < '2000-01-01' || $v > '2100-12-31') fail("Invalid $key.");
    return $v;
}
function activityType(array $d): string {
    $v = textValue($d,'type',20);
    if (!in_array($v,['Running','Cycling','Weightlifting','Walking'],true)) fail('Choose a supported activity.');
    return $v;
}
function authenticatedUser(): int {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer ([a-f0-9]{64})$/D', $header, $m)) fail('Please sign in again.',401);
    $q=db()->prepare('SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>UTC_TIMESTAMP()');
    $q->execute([hash('sha256',$m[1])]);
    $id=$q->fetchColumn();
    if (!$id) fail('Your session has expired. Please sign in again.',401);
    return (int)$id;
}
function issueSession(int $id, string $name): never {
    $token=bin2hex(random_bytes(32));
    db()->prepare('DELETE FROM sessions WHERE expires_at<UTC_TIMESTAMP()')->execute();
    db()->prepare('INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 7 DAY))')
        ->execute([hash('sha256',$token),$id]);
    respond(['token'=>$token,'user'=>['id'=>$id,'name'=>$name]]);
}
