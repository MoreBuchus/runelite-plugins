package com.radiusmarkers;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Varbits;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

class RadiusMarkerMinimapOverlay extends Overlay
{
	private static final int TILE_SIZE = 4;

	private final Client client;
	private final RadiusMarkerConfig config;
	private final RadiusMarkerPlugin plugin;

	@Inject
	RadiusMarkerMinimapOverlay(Client client, RadiusMarkerConfig config, RadiusMarkerPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;

		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.showMinimap())
		{
			drawMinimap(graphics);
		}
		return null;
	}

	private void drawMinimap(Graphics2D graphics)
	{
		final List<ColourRadiusMarker> markers = plugin.getMarkers();
		final List<NPC> npcs = client.getNpcs();

		for (final ColourRadiusMarker marker : markers)
		{
			if (!marker.isVisible())
			{
				continue;
			}

			final WorldPoint worldPoint = marker.getWorldPoint();
			final boolean excludeCorner = AttackType.MELEE.equals(marker.getAttackType()) ||
				AttackType.HALBERD.equals(marker.getAttackType());

			if (marker.isRetreatInteractionVisible())
			{
				drawSquare(graphics, worldPoint, marker.getRetreatInteractionColour(),
					marker.getRetreatInteractionRadius(), 1, false);
			}

			if (marker.isAggressionVisible())
			{
				drawSquare(graphics, worldPoint, marker.getAggressionColour(),
					marker.getAggressionRadius(), client.getNpcDefinition(marker.getNpcId()).getSize(), excludeCorner);
			}

			if (marker.isMaxVisible())
			{
				drawSquare(graphics, worldPoint, marker.getMaxColour(), marker.getMaxRadius(), 1, false);
			}

			if (marker.isWanderVisible())
			{
				drawSquare(graphics, worldPoint, marker.getWanderColour(), marker.getWanderRadius(), 1, false);
			}

			if (marker.isSpawnVisible())
			{
				drawSquare(graphics, worldPoint, marker.getSpawnColour(), 0, 1, false);
			}

			for (NPC npc : npcs)
			{
				if (npc.getId() != marker.getNpcId())
				{
					continue;
				}

				final WorldPoint npcLocation = npc.getWorldLocation();
				final int size = npc.getComposition().getSize();

				if (marker.isInteractionVisible())
				{
					WorldPoint worldLocation = RadiusOrigin.DYNAMIC.equals(marker.getInteractionOrigin()) ?
						npcLocation : worldPoint;
					drawSquare(graphics, worldLocation, marker.getInteractionColour(),
						marker.getInteractionRadius(), size, false);
				}

				if (marker.isHuntVisible())
				{
					drawSquare(graphics, npcLocation, marker.getHuntColour(), marker.getHuntRadius(), 1, false);
				}

				if (marker.isAttackVisible())
				{
					drawSquare(graphics, npcLocation, marker.getAttackColour(),
						marker.getAttackRadius(), size, excludeCorner);
				}
			}
		}
	}

	private void drawSquare(Graphics2D graphics, WorldPoint center, Color color,
		int radius, int size, boolean excludeCorner)
	{
		final WorldPoint southWest = center.dx(-radius).dy(-radius);
		final int diameter = 2 * radius + size;

		Area minimapClipArea = getMinimapClipArea();
		if (minimapClipArea == null)
		{
			return;
		}

		graphics.setColor(color);
		graphics.setClip(minimapClipArea);

		GeneralPath path = new GeneralPath();
		if (radius > 0 && excludeCorner)
		{
			updateLine(path, new WorldPoint[]
			{
				southWest.dx(1).dy(1), southWest.dx(1), southWest.dx(diameter - 1), southWest.dx(diameter - 1).dy(1)
			});
			updateLine(path, new WorldPoint[]
			{
				southWest.dx(diameter - 1).dy(1), southWest.dx(diameter).dy(1),
				southWest.dx(diameter).dy(diameter - 1), southWest.dx(diameter - 1).dy(diameter - 1)
			});
			updateLine(path, new WorldPoint[]
			{
				southWest.dx(1).dy(1), southWest.dy(1), southWest.dy(diameter - 1), southWest.dx(1).dy(diameter - 1)
			});
			updateLine(path, new WorldPoint[]
			{
				southWest.dx(1).dy(diameter - 1), southWest.dx(1).dy(diameter),
				southWest.dx(diameter - 1).dy(diameter), southWest.dx(diameter - 1).dy(diameter - 1)
			});
		}
		else
		{
			updateLine(path, new WorldPoint[] { southWest, southWest.dx(diameter) });
			updateLine(path, new WorldPoint[] { southWest.dx(diameter), southWest.dx(diameter).dy(diameter) });
			updateLine(path, new WorldPoint[] { southWest, southWest.dy(diameter) });
			updateLine(path, new WorldPoint[] { southWest.dy(diameter), southWest.dx(diameter).dy(diameter) });
		}
		graphics.draw(path);
	}

	private void updateLine(GeneralPath path, WorldPoint[] worldPoints)
	{
		for (int i = 1; i < worldPoints.length; i++)
		{
			Point previous = worldToMinimap(worldPoints[i - 1]);
			boolean hasFirst = false;
			if (previous != null)
			{
				path.moveTo(previous.getX(), previous.getY());
				hasFirst = true;
			}
			Point current = worldToMinimap(worldPoints[i]);
			if (hasFirst && current != null)
			{
				path.lineTo(current.getX(), current.getY());
			}
		}
	}

	private Point worldToMinimap(final WorldPoint worldPoint)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		final WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		final LocalPoint localLocation = client.getLocalPlayer().getLocalLocation();
		final LocalPoint playerLocalPoint = LocalPoint.fromWorld(client, playerLocation);

		if (playerLocalPoint == null)
		{
			return null;
		}

		final int offsetX = playerLocalPoint.getX() - localLocation.getX();
		final int offsetY = playerLocalPoint.getY() - localLocation.getY();

		final int x = (worldPoint.getX() - playerLocation.getX()) * TILE_SIZE + offsetX / 32 - TILE_SIZE / 2;
		final int y = (worldPoint.getY() - playerLocation.getY()) * TILE_SIZE + offsetY / 32 - TILE_SIZE / 2 + 1;

		final int angle = client.getMapAngle() & 0x7FF;

		final int sin = (int) (65536.0D * Math.sin((double) angle * Perspective.UNIT));
		final int cos = (int) (65536.0D * Math.cos((double) angle * Perspective.UNIT));

		final Widget minimapDrawWidget = getMinimapDrawWidget();
		if (minimapDrawWidget == null || minimapDrawWidget.isHidden())
		{
			return null;
		}

		final int xx = y * sin + cos * x >> 16;
		final int yy = sin * x - y * cos >> 16;

		final Point loc = minimapDrawWidget.getCanvasLocation();
		final int minimapX = loc.getX() + xx + minimapDrawWidget.getWidth() / 2;
		final int minimapY = loc.getY() + yy + minimapDrawWidget.getHeight() / 2;

		return new Point(minimapX, minimapY);
	}

	private Widget getMinimapDrawWidget()
	{
		Widget minimapDrawArea;
		if (client.isResized())
		{
			if (client.getVar(Varbits.SIDE_PANELS) == 1)
			{
				minimapDrawArea = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA);
			}
			else
			{
				minimapDrawArea = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_STONES_DRAW_AREA);
			}
		}
		else
		{
			minimapDrawArea = client.getWidget(WidgetInfo.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
		}
		return minimapDrawArea;
	}

	private Area getMinimapClipArea()
	{
		Widget minimapDrawArea = getMinimapDrawWidget();

		if (minimapDrawArea == null || minimapDrawArea.isHidden())
		{
			return null;
		}

		Rectangle bounds = minimapDrawArea.getBounds();
		Ellipse2D ellipse = new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());

		return new Area(ellipse);
	}
}