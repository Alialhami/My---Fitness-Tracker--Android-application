<?php
declare(strict_types=1);
require dirname(__DIR__).'/bootstrap.php';
$action=$_GET['action'] ?? '';
$method=$_SERVER['REQUEST_METHOD'];
$allowed=['health'=>'GET','register'=>'POST','login'=>'POST','logout'=>'POST','workouts'=>'GET',
    'save_workout'=>'POST','delete_workout'=>'POST','goals'=>'GET','save_goal'=>'POST','delete_goal'=>'POST'];
if (!isset($allowed[$action])) fail('Endpoint not found.',404);
if ($allowed[$action] !== $method) fail('Method not allowed.',405);
if ($action==='health') { db()->query('SELECT 1'); respond(['status'=>'ok','service'=>'Fitness Tracker']); }
if ($action==='register' || $action==='login') {
    $d=input();
    $email=strtolower(textValue($d,'email',190));
    if (!filter_var($email,FILTER_VALIDATE_EMAIL)) fail('Enter a valid email address.');
    $password=$d['password'] ?? '';
    if (!is_string($password) || strlen($password)<8 || strlen($password)>72) fail('Use a password of 8 to 72 bytes.');
    if ($action==='register') {
        $name=textValue($d,'name',100);
        try { db()->prepare('INSERT INTO users(name,email,password_hash) VALUES(?,?,?)')
            ->execute([$name,$email,password_hash($password,PASSWORD_DEFAULT)]); }
        catch(PDOException $e) { if ($e->getCode()==='23000') fail('This email is already registered.',409); throw $e; }
        issueSession((int)db()->lastInsertId(),$name);
    }
    $q=db()->prepare('SELECT id,name,password_hash FROM users WHERE email=?');$q->execute([$email]);$u=$q->fetch();
    // Always run password verification to reduce account-enumeration timing differences.
    $hash=$u['password_hash'] ?? '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';
    if (!password_verify($password,$hash) || !$u) { usleep(200000); fail('Email or password is incorrect.',401); }
    issueSession((int)$u['id'],$u['name']);
}
$uid=authenticatedUser();
if ($action==='logout') {
    $token=substr($_SERVER['HTTP_AUTHORIZATION'],7);
    db()->prepare('DELETE FROM sessions WHERE token_hash=?')->execute([hash('sha256',$token)]);
    respond(['ok'=>true]);
}
if ($action==='workouts') {
    $q=db()->prepare('SELECT id,client_id,type,duration,distance,weight,sets,reps,date,latitude,longitude,notes FROM workouts WHERE user_id=? ORDER BY date DESC,id DESC');
    $q->execute([$uid]);respond(['workouts'=>$q->fetchAll()]);
}
if ($action==='save_workout') {
    $d=input();$type=activityType($d);$duration=numberValue($d,'duration',1,1440,true);$date=dateValue($d,'date');
    if ($date>gmdate('Y-m-d',time()+14*3600)) fail('A completed workout cannot be in the future.');
    $client=textValue($d,'client_id',36);
    if (!preg_match('/^[a-f0-9-]{36}$/D',$client)) fail('Invalid client identifier.');
    $strength=$type==='Weightlifting';
    $distance=$strength?null:numberValue($d,'distance',0.01,99999);
    $weight=$strength?numberValue($d,'weight',0.01,99999):null;
    $sets=$strength?numberValue($d,'sets',1,1000,true):null;
    $reps=$strength?numberValue($d,'reps',1,1000,true):null;
    $lat=$d['latitude'] ?? null;$lon=$d['longitude'] ?? null;
    if (($lat===null)!==($lon===null)) fail('Both location coordinates are required.');
    if ($lat!==null) {$lat=numberValue($d,'latitude',-90,90);$lon=numberValue($d,'longitude',-180,180);}
    $notes=textValue($d,'notes',500,false);
    $id=isset($d['id'])?numberValue($d,'id',0,PHP_INT_MAX,true):0;
    if ($id>0) {
        $q=db()->prepare('SELECT id FROM workouts WHERE id=? AND user_id=?');$q->execute([$id,$uid]);
        if (!$q->fetch()) fail('Workout not found.',404);
        db()->prepare('UPDATE workouts SET type=?,duration=?,distance=?,weight=?,sets=?,reps=?,date=?,latitude=?,longitude=?,notes=? WHERE id=? AND user_id=?')
            ->execute([$type,$duration,$distance,$weight,$sets,$reps,$date,$lat,$lon,$notes,$id,$uid]);
    } else {
        // A stable UUID makes retrying a pending offline workout safe.
        $q=db()->prepare('INSERT INTO workouts(user_id,client_id,type,duration,distance,weight,sets,reps,date,latitude,longitude,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)');
        $q->execute([$uid,$client,$type,$duration,$distance,$weight,$sets,$reps,$date,$lat,$lon,$notes]);
        $id=(int)db()->lastInsertId();
    }
    respond(['ok'=>true,'id'=>(int)$id]);
}
if ($action==='delete_workout' || $action==='delete_goal') {
    $d=input();$id=numberValue($d,'id',1,PHP_INT_MAX,true);
    $table=$action==='delete_workout'?'workouts':'goals';
    $q=db()->prepare("DELETE FROM $table WHERE id=? AND user_id=?");$q->execute([$id,$uid]);
    if (!$q->rowCount()) fail('Record not found.',404);
    respond(['ok'=>true]);
}
if ($action==='goals') {
    $q=db()->prepare('SELECT g.id,g.type,g.target_minutes,g.start_date,g.end_date,COALESCE(SUM(w.duration),0) AS completed_minutes FROM goals g LEFT JOIN workouts w ON w.user_id=g.user_id AND w.type=g.type AND w.date BETWEEN g.start_date AND g.end_date WHERE g.user_id=? GROUP BY g.id ORDER BY g.end_date DESC,g.id DESC');
    $q->execute([$uid]);respond(['goals'=>$q->fetchAll()]);
}
if ($action==='save_goal') {
    $d=input();$type=activityType($d);$target=numberValue($d,'target_minutes',1,100000,true);
    $start=dateValue($d,'start_date');$end=dateValue($d,'end_date');
    if ($end<$start) fail('End date must be on or after start date.');
    db()->prepare('INSERT INTO goals(user_id,type,target_minutes,start_date,end_date) VALUES(?,?,?,?,?)')->execute([$uid,$type,$target,$start,$end]);
    respond(['ok'=>true,'id'=>(int)db()->lastInsertId()]);
}
