paulo - passo 1
# 1. Parar todos os containers em execução
docker stop $(docker ps -q)

# 2. Remover todos os containers
docker rm $(docker ps -aq)

# 3. Remover todas as imagens
docker rmi $(docker images -q)

# 4. Remover volumes (dados persistidos)
docker volume prune -f

# 5. Limpar cache de build
docker builder prune -f
docker system prune -af

# Verificar que tudo foi removido
docker ps -a
docker images



paulo - passo 2

# Remover tudo primeiro
docker-compose down -v

# Construir e iniciar
docker-compose up -d

# Aguarde
Start-Sleep -Seconds 40

# Ver logs
docker-compose logs -f wildfly

# Acessar
Start-Process "http://localhost:8080/erp-barbershop/"
