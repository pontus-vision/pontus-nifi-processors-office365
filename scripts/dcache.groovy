/*
 * dcache.groovy
 */
if(!args || args.length < 3) {
  println """Usage: groovy dcache.groovy <hostname> <port> <command> <args>

Where <command> is one of the following:

get: Retrieves the values for the keys (provided as arguments)
remove: Removes the keys (specified as arguments)
put: Sets the given keys to the given values, specified as arguments in the form: key1 value1 key2 value2 ... keyN valueN
keys: Retrieves all keys from the cache
"""
  System.exit(1)
}
def protocolVersion = 1
def hostname = args[0]
def port = Integer.parseInt(args[1])
def command = args[2]

s = new Socket(hostname, port)

s.withStreams { input, output ->
  def dos = new DataOutputStream(output)
  def dis = new DataInputStream(input)
  
  // Negotiate handshake/version
  dos.write('NiFi'.bytes)
  dos.writeInt(protocolVersion)
  dos.flush()  
 
  status = dis.read()
  while(status == 21) {
     protocolVersion = dis.readInt()
     dos.writeInt(protocolVersion)
     dos.flush()
     status = dis.read()
  }
  
  switch(command.toLowerCase()) {
    case 'get':
      def keys = args[3..(args.length-1)]
      // Get entries
      keys.each {
        key = it.getBytes('UTF-8')
        dos.writeUTF('get')
        def baos = new ByteArrayOutputStream()
        baos.write(key)
        dos.writeInt(baos.size())  
        baos.writeTo(dos)
        dos.flush()
        def length = dis.readInt()
        def bytes = new byte[length]
        dis.readFully(bytes)
        println "$it = ${new String(bytes)}" 
      }
      break
    case 'remove':
      def keys = args[3..(args.length-1)]
      // Remove entries
      keys.each {
          key = it.getBytes('UTF-8')
          dos.writeUTF('remove')
          def baos = new ByteArrayOutputStream()
          baos.write(key)
          dos.writeInt(baos.size())  
          baos.writeTo(dos)
          dos.flush()
          def success = dis.readBoolean()
          println success ? "Removed $it" : "Could not remove $it"
      }
      break
    case 'put':
      def map = [:]
      for(int i=3;i<args.length;i+=2) {
        if(i == args.length-1) {
          println "Key ${args[i]} has no value specified, ignoring..."
        } else {
          map.put(args[i], args[i+1])
        }
      }
      // Put entries
      map.each { k,v ->
          key = k.getBytes('UTF-8')
          value = v.getBytes('UTF-8')
          dos.writeUTF('put')
          def baos = new ByteArrayOutputStream()
          baos.write(key)
          dos.writeInt(baos.size())  
          baos.writeTo(dos)
          baos = new ByteArrayOutputStream()
          baos.write(value)
          dos.writeInt(baos.size())  
          baos.writeTo(dos)
          dos.flush()
          def success = dis.readBoolean()
          println success ? "Set $k = $v" : "Could not set $k = $v"
      }
      break
      case 'keys':
          dos.writeUTF('keySet')
          dos.flush()
          int numKeys = dis.readInt()
          (0..numKeys-1).each { 
            def length = dis.readInt()
            def bytes = new byte[length]
            dis.readFully(bytes)
            println new String(bytes)
          }
          break
    default:
      println "$command is not a recognized command"
      break

    }
  
  // Close 
  dos.writeUTF("close");
  dos.flush();
}

