BASE_URL ?= http://localhost:8080
ADMIN_SECRET ?= change-me

run:    ; mvn -q spring-boot:run
build:  ; mvn -q -DskipTests package
docker: ; docker build -t seatbook .
burst:  ; java scripts/Burst.java $(BASE_URL) $(ADMIN_SECRET)
