from flask import Flask
from routes.auth_routes import auth_bp
from routes.paper_routes import paper_bp
from routes.citation_routes import citation_bp
from routes.annot_routes import annot_bp
from routes.reference_routes import reference_routes

app = Flask(__name__)

app.register_blueprint(auth_bp)
app.register_blueprint(paper_bp)
app.register_blueprint(citation_bp)
app.register_blueprint(annot_bp)
app.register_blueprint(reference_routes)

if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0")
