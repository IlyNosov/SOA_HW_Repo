from fastapi import FastAPI
from fastapi.responses import JSONResponse

app = FastAPI(title="Marketplace")

@app.get("/health")
async def health_check():
    return JSONResponse(status_code=200, content={"status": "ok"})

@app.get("/")
async def root():
    return {"service": "Marketplace", "message": "Hello!"}