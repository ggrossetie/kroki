target "kroki-gateway" {
  context = "server"
  contexts = {
    nomnoml = "./nomnoml"
    vega = "./vega"
    wavedrom = "./wavedrom"
    bytefield = "./bytefield"
  }
  dockerfile = "ops/docker/jdk11-alpine/Dockerfile"
  tags = ["yuzutech/kroki:latest"]
}
