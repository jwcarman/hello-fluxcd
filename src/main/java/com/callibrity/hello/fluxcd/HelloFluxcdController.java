package com.callibrity.hello.fluxcd;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
public class HelloFluxcdController {

    @GetMapping
    public String hello() {
        return "Hello, Flux CD!";
    }

}
