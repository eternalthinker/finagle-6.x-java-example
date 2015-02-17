# finagle-6.x-java-example
Miscellaneous multi-server Java examples with Finagle (6.6.2)

###Example 1:  
**Client <-JSON-> HttpServer <-JSON-> HttpServer2**

*MainServer : port 9001*  
*HelperServer : port 9004*

**1)** JSON request from client. This can be a curl request.  
```bash
curl -i -H "Content-Type: application/json" -d '{"pname": "p123"}' http://localhost:9001
```
**2)** On receiving the request, MainServer sends an async JSON request to HelperServer  
**3)** HelperServer responds to MainServer  
**4)** MainServer, on receiving the async response from Helper, finally sends the JSON response to client  
  
###Example 2: 
**Client <-JSON-> HttpServer <-JSON-> Http/Redis-Server <-REDIS-> Redis-Cache**

*CookieServer : port 8000*  
*RedisServer : port 8002*

**1)** Request sent from client. This can be a curl request, but a browser will give access to an interface and cookie storage.  
**2)** On receiving the request, CookieServer analyzes/sets the cookie and retrive any stored data from RedisServer  
**3)** RedisServer queries the Redis instance running on port `7000` asynchronously  
**4)** Upon retrieving info from Redis Cache, the RedisServer responds to CookieServer in JSON  
**5)** CookieServer, on receiving the async response from Helper, finally sends the JSON response to client. In browser, the interface is changed according to the updated options  

