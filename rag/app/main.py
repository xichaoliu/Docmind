"""启动 uvicorn app.main:app --reload"""

import uvicorn
from app.app import app

def main() -> None:
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        reload=True
    )


if __name__ == "__main__":
    main()
