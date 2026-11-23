"""Run against a dedicated test database: python smoke.py http://127.0.0.1:8088
Creates disposable test accounts. Never point this script at a production database.
"""
import json,sys,uuid,urllib.request,urllib.error,datetime
BASE=sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:8088'
results=[]
def request(action,data=None,token='',expected=200):
    req=urllib.request.Request(BASE+'/index.php?action='+action,
        data=json.dumps(data).encode() if data is not None else None,
        headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
    try:
        with urllib.request.urlopen(req,timeout=15) as r: status=r.status;out=json.load(r)
    except urllib.error.HTTPError as e: status=e.code;out=json.load(e)
    assert status==expected,(action,status,out,expected)
    return out
def check(name,test):
    test();results.append(name);print('PASS',name,flush=True)
check('Health connects to MySQL',lambda:request('health'))
check('Authentication required',lambda:request('workouts',expected=401))
email='test-'+uuid.uuid4().hex+'@example.test'
data={'name':'Test Athlete','email':email,'password':'Test-Only-Password-42!'}
a=request('register',data);ta=a['token']
check('Registration returns a token',lambda: (_ for _ in ()).throw(AssertionError()) if len(ta)!=64 else None)
check('Duplicate email rejected',lambda:request('register',data,expected=409))
check('Wrong password rejected',lambda:request('login',dict(data,password='incorrect-password'),expected=401))
check('Login succeeds',lambda:request('login',data))
tb=request('register',dict(data,email='other-'+email))['token']
today=datetime.date.today().isoformat()
run={'client_id':str(uuid.uuid4()),'type':'Running','duration':30,'distance':5,'date':today,'latitude':32.8872,'longitude':13.1913,'notes':'Test run'}
w=request('save_workout',run,ta)['id']
def assert_equal(a,b): assert a==b,(a,b)
check('Duplicate offline retry has same id',lambda:assert_equal(request('save_workout',run,ta)['id'],w))
check('User reads only own workouts',lambda:assert_equal(len(request('workouts',token=ta)['workouts']),1))
check('Other user cannot read workout',lambda:assert_equal(request('workouts',token=tb)['workouts'],[]))
check('Other user cannot edit workout',lambda:request('save_workout',dict(run,id=w,duration=50),tb,404))
check('Other user cannot delete workout',lambda:request('delete_workout',{'id':w},tb,404))
check('Zero duration rejected',lambda:request('save_workout',dict(run,duration=0),ta,422))
check('Fractional duration rejected',lambda:request('save_workout',dict(run,duration=2.5),ta,422))
check('Invalid location rejected',lambda:request('save_workout',dict(run,latitude=100),ta,422))
check('Invalid date rejected',lambda:request('save_workout',dict(run,date='2026-02-30'),ta,422))
check('Invalid activity rejected',lambda:request('save_workout',dict(run,type='Unknown'),ta,422))
check('Editing own workout succeeds',lambda:request('save_workout',dict(run,id=w,duration=40),ta))
goal={'type':'Running','target_minutes':90,'start_date':today,'end_date':today}
g=request('save_goal',goal,ta)['id']
check('Goal progress calculated from database',lambda:assert_equal(int(request('goals',token=ta)['goals'][0]['completed_minutes']),40))
check('Goal dates validated',lambda:request('save_goal',dict(goal,end_date='2000-01-01'),ta,422))
check('Other user cannot delete goal',lambda:request('delete_goal',{'id':g},tb,404))
lift={'client_id':str(uuid.uuid4()),'type':'Weightlifting','duration':40,'weight':30,'sets':3,'reps':10,'date':today}
check('Weightlifting stores sets reps and load',lambda:request('save_workout',lift,ta))
check('Deleting own goal succeeds',lambda:request('delete_goal',{'id':g},ta))
check('Deleting own workout succeeds',lambda:request('delete_workout',{'id':w},ta))
check('Logout revokes token',lambda:request('logout',{},ta))
check('Revoked token cannot access data',lambda:request('workouts',token=ta,expected=401))
print(json.dumps({'passed':len(results),'tests':results},indent=2))
