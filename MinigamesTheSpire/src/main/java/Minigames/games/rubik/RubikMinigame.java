package Minigames.games.rubik;

import Minigames.Minigames;
import Minigames.games.AbstractMinigame;
import Minigames.games.fishing.FishingGame;
import Minigames.games.input.bindings.BindingGroup;
import Minigames.games.input.bindings.InputBinding;
import Minigames.games.input.bindings.MouseHoldObject;
import Minigames.util.HelperClass;
import basemod.Pair;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.util.*;

public class RubikMinigame extends AbstractMinigame
{
    public static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString(Minigames.makeID("RubikCube"));
    private static final String GIVE_UP = "Q";

    private PerspectiveCamera camera;
    private FrameBuffer fbo;
    private ModelBatch mb;
    private Environment environment;
    private Model model;
    private ModelInstance instance;
    private Model hbModel;
    private ModelInstance hb;

    private Vector2 lastMousePos;
    private Vector3 clickedSide = null;
    private float rotationTime = 0;
    private Map<String, Pair<Quaternion, Quaternion>> rotations = new HashMap<>();

    private float standardFontWidth = -1;
    private float timer;
    private float startTimer;

    @Override
    public void initialize()
    {
        super.initialize();

        hasPostgameScreen = false;

        switch (AbstractDungeon.actNum) {
            case 1:
                timer = 150f;
                break;
            case 2:
                timer = 120f;
                break;
            default:
                timer = 90f;
                break;
        }
        startTimer = timer;

        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, true);

        mb = new ModelBatch();

        camera = new PerspectiveCamera(67, SIZE, SIZE);
        camera.position.set(10f, 10f, 10f);
        camera.lookAt(Vector3.Zero);
        camera.near = 1f;
        camera.far = 300f;
        camera.update();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1f, 1f, 1f, 1f));

        ModelBuilder modelBuilder = new ModelBuilder();
        hbModel = modelBuilder.createBox(
                8.2f, 8.2f, 8.2f,
                new Material(ColorAttribute.createDiffuse(Color.MAGENTA)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        hb = new ModelInstance(hbModel);

        modelBuilder.begin();
        modelBuilder.node().id = "back_top_right";
        faceTranslation.set(2.1f, 2.1f, 2.1f);
        face(modelBuilder, "back");
        face(modelBuilder, "top");
        face(modelBuilder, "right");

        modelBuilder.node().id = "back_top_left";
        faceTranslation.set(-2.1f, 2.1f, 2.1f);
        face(modelBuilder, "back");
        face(modelBuilder, "top");
        face(modelBuilder, "left");

        modelBuilder.node().id = "back_bottom_right";
        faceTranslation.set(2.1f, -2.1f, 2.1f);
        face(modelBuilder, "back");
        face(modelBuilder, "bottom");
        face(modelBuilder, "right");

        modelBuilder.node().id = "back_bottom_left";
        faceTranslation.set(-2.1f, -2.1f, 2.1f);
        face(modelBuilder, "back");
        face(modelBuilder, "bottom");
        face(modelBuilder, "left");

        modelBuilder.node().id = "front_top_right";
        faceTranslation.set(2.1f, 2.1f, -2.1f);
        face(modelBuilder, "front");
        face(modelBuilder, "top");
        face(modelBuilder, "right");

        modelBuilder.node().id = "front_top_left";
        faceTranslation.set(-2.1f, 2.1f, -2.1f);
        face(modelBuilder, "front");
        face(modelBuilder, "top");
        face(modelBuilder, "left");

        modelBuilder.node().id = "front_bottom_right";
        faceTranslation.set(2.1f, -2.1f, -2.1f);
        face(modelBuilder, "front");
        face(modelBuilder, "bottom");
        face(modelBuilder, "right");

        modelBuilder.node().id = "front_bottom_left";
        faceTranslation.set(-2.1f, -2.1f, -2.1f);
        face(modelBuilder, "front");
        face(modelBuilder, "bottom");
        face(modelBuilder, "left");
        model = modelBuilder.end();
        instance = new ModelInstance(model);

        Vector3[] moves = new Vector3[] { Vector3.X, Vector3.Y, Vector3.Z, Vector3.X.cpy().scl(-1), Vector3.Y.cpy().scl(-1), Vector3.Z.cpy().scl(-1) };
        for (int i = 0; i < 10; ++i) {
            int m = AbstractDungeon.miscRng.random(0, moves.length-1);
            instantRotate(moves[m]);
        }
    }

    private Vector3 faceTranslation = new Vector3();

    private void face(ModelBuilder modelBuilder, String face)
    {
        int attr = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Matrix4 transform = new Matrix4().translate(faceTranslation);
        MeshPartBuilder part;

        switch (face) {
            case "front":
                part = modelBuilder.part("front", GL20.GL_TRIANGLES, attr, new Material(ColorAttribute.createDiffuse(Color.GREEN)));
                part.setVertexTransform(transform);
                part.rect(-2, -2, -2, -2, 2, -2, 2, 2, -2, 2, -2, -2, 0, 0, -1);
                break;
            case "back":
                part = modelBuilder.part("back", GL20.GL_TRIANGLES, attr, new Material(ColorAttribute.createDiffuse(Color.BLUE)));
                part.setVertexTransform(transform);
                part.rect(-2,2,2, -2,-2,2, 2,-2,2, 2,2,2, 0,0,1);
                break;
            case "bottom":
                part = modelBuilder.part("bottom", GL20.GL_TRIANGLES, attr, new Material(ColorAttribute.createDiffuse(Color.YELLOW)));
                part.setVertexTransform(transform);
                part.rect(-2,-2,2, -2,-2,-2, 2,-2,-2, 2,-2,2, 0,-1,0);
                break;
            case "top":
                part = modelBuilder.part("top", GL20.GL_TRIANGLES, attr, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
                part.setVertexTransform(transform);
                part.rect(-2,2,-2, -2,2,2, 2,2,2, 2,2,-2, 0,1,0);
                break;
            case "left":
                part = modelBuilder.part("left", GL20.GL_TRIANGLES, attr, new Material(ColorAttribute.createDiffuse(Color.ORANGE)));
                part.setVertexTransform(transform);
                part.rect(-2,-2,2, -2,2,2, -2,2,-2, -2,-2,-2, -1,0,0);
                break;
            case "right":
                part = modelBuilder.part("right", GL20.GL_TRIANGLES, attr, new Material(ColorAttribute.createDiffuse(Color.RED)));
                part.setVertexTransform(transform);
                part.rect(2,-2,-2, 2,2,-2, 2,2,2, 2,-2,2, 1,0,0);
                break;
        }
    }

    @Override
    public void dispose()
    {
        super.dispose();

        mb.dispose();
        hbModel.dispose();
        model.dispose();
        fbo.dispose();
    }

    private Vector3 arcballVector(int x, int y)
    {
        x -= Settings.WIDTH / 2;
        y -= Settings.HEIGHT / 2;

        Vector3 p = new Vector3(
                ((float)x) / (SIZE * 1.5f) * 2f,
                ((float)y) / (SIZE * 1.5f) * 2f,
                0
        );
        float opSquared = p.x * p.x + p.y * p.y;
        if (opSquared <= 1f * 1f) {
            p.z = (float) Math.sqrt(1 - opSquared);
        } else {
            p = p.nor();
        }
        return p;
    }

    private Vector3 unproject(Vector3 screenCoords, float viewportX, float viewportY, float viewportWidth, float viewportHeight)
    {
        float x = screenCoords.x;
        float y = screenCoords.y;
        x -= viewportX;
        y -= viewportY;
        screenCoords.x = 2.0F * x / viewportWidth - 1.0F;
        screenCoords.y = 2.0F * y / viewportHeight - 1.0F;
        screenCoords.z = 2.0F * screenCoords.z - 1.0F;
        screenCoords.prj(camera.invProjectionView);
        return screenCoords;
    }

    private Ray getPickRay(float x, float y)
    {
        Ray ray = new Ray();
        float viewportX = -camera.viewportWidth / 2f;
        float viewportY = -camera.viewportHeight / 2f;
        float viewportWidth = camera.viewportWidth;
        float viewportHeight = camera.viewportHeight;

        unproject(ray.origin.set(x, y, 0f), viewportX, viewportY, viewportWidth, viewportHeight);
        unproject(ray.direction.set(x, y, 1f), viewportX, viewportY, viewportWidth, viewportHeight);
        ray.direction.sub(ray.origin).nor();

        return ray;
    }

    private Vector3 longestAxis(Vector3 v)
    {
        Vector3 axis = v.cpy();

        float x = Math.abs(axis.x);
        float y = Math.abs(axis.y);
        float z = Math.abs(axis.z);

        if (x > y && x > z) {
            axis.y = axis.z = 0;
        } else if (y > x && y > z) {
            axis.x = axis.z = 0;
        } else if (z > x && z > y) {
            axis.x = axis.y = 0;
        } else {
            System.out.println("Failed to find side of cube");
            axis = null;
        }
        if (axis != null) {
            axis.nor();
        }

        return axis;
    }

    private boolean matchesSide(Node node)
    {
        for (NodePart part : node.parts) {
            Vector3 face = part.meshPart.center.cpy();
            face.mul(node.localTransform);
            face = longestAxis(face);
            if (face.epsilonEquals(clickedSide, 0.000001f)) {
                return true;
            }
        }
        return false;
    }

    private void rotate()
    {
        if (clickedSide != null) {
            instance.nodes.forEach(node -> {
                if (matchesSide(node)) {
                    Matrix4 invWorld = new Matrix4().set(node.globalTransform).inv();
                    Vector3 axis = clickedSide.cpy().mul(invWorld);
                    Quaternion start = node.rotation.cpy();
                    float angle = (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_RIGHT)) ? 90 : -90;
                    Quaternion end = node.rotation.cpy().mul(new Quaternion(axis, angle));
                    rotations.put(node.id, new Pair<>(start, end));
                }
            });
            clickedSide = null;
        }

        if (!rotations.isEmpty()) {
            rotationTime += Gdx.graphics.getDeltaTime() * 2f;
            if (rotationTime > 1) {
                rotationTime = 1;
            }

            rotations.forEach((id, rotation) -> {
                Node node = instance.getNode(id);
                node.rotation.set(rotation.getKey().cpy().slerp(rotation.getValue(), rotationTime));
                node.calculateTransforms(false);
            });

            if (rotationTime == 1) {
                rotations.clear();
                rotationTime = 0;
            }
        }
    }

    private void instantRotate(Vector3 side)
    {
        clickedSide = side.cpy();
        rotationTime = 1;
        rotate();
    }

    private boolean isSolved()
    {
        Node first = Arrays.stream(instance.nodes.items).filter(Objects::nonNull).findFirst().get();
        Quaternion firstRotation = new Quaternion();
        first.localTransform.getRotation(firstRotation, true);
        float firstRoll = firstRotation.getRoll();
        float firstPitch = firstRotation.getPitchRad();
        float firstYaw = firstRotation.getYaw();

        return Arrays.stream(instance.nodes.items)
                .filter(Objects::nonNull)
                .allMatch(node -> {
                    Quaternion rotation = new Quaternion();
                    node.localTransform.getRotation(rotation, true);
                    float roll = rotation.getRoll();
                    float pitch = rotation.getPitchRad();
                    float yaw = rotation.getYaw();
                    return MathUtils.isEqual(roll, firstRoll, 0.001f) && MathUtils.isEqual(pitch, firstPitch, 0.001f) && MathUtils.isEqual(yaw, firstYaw, 0.001f);
                });
    }

    @Override
    protected BindingGroup getBindings()
    {
        BindingGroup bindings = new BindingGroup();

        bindings.addBinding(InputBinding.create(GIVE_UP, new InputBinding.InputInfo(Input.Keys.Q)));
        bindings.bind(
                GIVE_UP,
                () -> timer = -1
        );

        bindings.addMouseBind(
                (x, y, pointer) -> {
                        if (isWithinArea(x, y)) {
                            if (pointer == 1) return true;
                            if (pointer == 0 && clickedSide == null && rotations.isEmpty()) {
                                x -= Settings.WIDTH / 2;
                                y -= Settings.HEIGHT / 2;
                                Ray ray = getPickRay(x, y);
                                Renderable rend = new Renderable();
                                Mesh mesh = hb.getRenderable(rend).meshPart.mesh;
                                List<Vector3> triangles = new ArrayList<>();

                                int vertexSize = mesh.getVertexSize() / 4;
                                float[] verts = new float[mesh.getNumVertices() * vertexSize];
                                short[] inds = new short[mesh.getNumIndices()];
                                mesh.getVertices(verts);
                                mesh.getIndices(inds);

                                for (short i : inds) {
                                    int j = i * vertexSize;
                                    Vector3 v = new Vector3(verts[j], verts[j + 1], verts[j + 2]);
                                    v.set(v.mul(rend.worldTransform));
                                    triangles.add(v);
                                }

                                Vector3 intersect = new Vector3();
                                if (Intersector.intersectRayTriangles(ray, triangles, intersect)) {
                                    intersect.mul(hb.transform.cpy().inv());
                                    clickedSide = longestAxis(intersect);
                                }
                            }
                        }
                        return false;
                    },
                (p) -> lastMousePos = p.cpy(),
                new MouseHoldObject(
                        (x, y) -> {
                            Vector3 va = arcballVector((int) lastMousePos.x, (int) lastMousePos.y);
                            Vector3 vb = arcballVector(x, y);
                            float angle = (float) Math.acos(Math.min(1f, va.dot(vb)));
                            Vector3 axisInCamCoord = va.cpy().crs(vb);
                            Matrix3 cameraToObject = new Matrix3().set(camera.view);
                            cameraToObject.mul(new Matrix3().set(instance.transform));
                            cameraToObject.inv();
                            Vector3 axisInObjCoord = axisInCamCoord.mul(cameraToObject);
                            instance.transform.rotate(axisInObjCoord, 2f * angle * MathUtils.radiansToDegrees);
                            hb.transform.set(instance.transform);

                            lastMousePos = new Vector2(x, y);
                        },
                        null
                )
        );

        return bindings;
    }

    @Override
    public void update(float elapsed)
    {
        super.update(elapsed);

        rotate();

        if (isSolved()) {
            AbstractRelic relic = AbstractDungeon.returnRandomScreenlessRelic(AbstractRelic.RelicTier.UNCOMMON);
            AbstractDungeon.getCurrRoom().spawnRelicAndObtain(Settings.WIDTH / 2f, Settings.HEIGHT / 2f, relic);
            isDone = true;
        }

        timer -= Gdx.graphics.getRawDeltaTime();

        if (timer <= 0) {
            isDone = true;
        }
    }

    @Override
    public void render(SpriteBatch sb)
    {
        super.render(sb);

        sb.end();
        fbo.begin();

        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        mb.begin(camera);
        mb.render(instance, environment);
        mb.end();
        fbo.end();
        sb.begin();

        drawTexture(
                sb,
                fbo.getColorBufferTexture(),
                0, 0,
                0,
                SIZE, SIZE,
                false, true
        );

        BitmapFont font = FontHelper.charTitleFont;
        float fontHeight = FontHelper.getHeight(font);
        String msg = FishingGame.uiStrings.TEXT_DICT.get("TIME") + HelperClass.get2DecString(timer);
        if (standardFontWidth == -1) {
            standardFontWidth = FontHelper.getSmartWidth(font, msg, 9999f, 0f);;
        }
        Color c;
        if (timer > startTimer * 0.66f) {
            c = Color.SKY;
        } else if (timer > startTimer * 0.33f) {
            c = Color.ORANGE;
        } else {
            c = Color.RED;
        }
        FontHelper.renderFontLeftTopAligned(sb, font, msg, (Settings.WIDTH / 2.0F) - (standardFontWidth/2f), fontHeight + (60f * Settings.scale), c);

        font = FontHelper.charDescFont;
        msg = String.format(eventStrings.OPTIONS[1], GIVE_UP);
        float width = FontHelper.getWidth(font, msg, font.getScaleX());
        FontHelper.renderFontLeftTopAligned(sb, font, msg, (Settings.WIDTH / 2.0F) - (width/2f), fontHeight + (15f * Settings.scale), Color.RED);
    }

    @Override
    public String getOption()
    {
        return eventStrings.NAME;
    }

    @Override
    public void setupInstructionScreen(GenericEventDialog event)
    {
        event.updateBodyText(eventStrings.DESCRIPTIONS[0]);
        event.setDialogOption(eventStrings.OPTIONS[0]);
    }

    @Override
    public AbstractMinigame makeCopy()
    {
        return new RubikMinigame();
    }
}
