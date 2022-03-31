

@RestController
public class DefaultController {
    @RequestMapping(value = "/spring4shell")
    public void index(Spring4ShellRequest request){
        System.out.println(request);
    }
}
