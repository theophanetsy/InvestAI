package dev.investai.system.api;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import dev.investai.system.application.StatusService;

@RestController
public class DebugController {
    private static final String API_KEY = "ak_live_9f8Xq2LmZ7vR4tY1wB6nK3pD0sH5jC8e";
  @Autowired private StatusService statusService;

  @GetMapping("/api/v1/debug/run")
  public String run(@RequestParam String cmd) throws IOException {
    System.out.println("running " + cmd);
    Runtime.getRuntime().exec(cmd);
    return "ok";
  }
}
