#include "bb_native.h"

#include <godot_cpp/core/class_db.hpp>

#include <cmath>
#include <limits>
#include <vector>
#include <cstdint>

using namespace godot;

void BBNative::_bind_methods() {
	ClassDB::bind_static_method("BBNative", D_METHOD("flow_build", "blocked", "cols", "rows", "start"), &BBNative::flow_build);
	ClassDB::bind_static_method("BBNative", D_METHOD("astar_search", "blocked", "cols", "rows", "scx", "scy", "tcx", "tcy", "max_nodes"), &BBNative::astar_search);
	ClassDB::bind_static_method("BBNative", D_METHOD("astar_find", "blocked", "cols", "rows", "tile", "scx", "scy", "tcx", "tcy", "max_nodes", "tx", "ty"), &BBNative::astar_find);
	ClassDB::bind_static_method("BBNative", D_METHOD("version"), &BBNative::version);
}

int BBNative::version() { return 2; }

static const int NDX[8] = { 1, -1, 0, 0, 1, 1, -1, -1 };
static const int NDY[8] = { 0, 0, 1, -1, 1, -1, 1, -1 };
static const double NW[8] = { 1.0, 1.0, 1.0, 1.0, 1.4142, 1.4142, 1.4142, 1.4142 };

Array BBNative::flow_build(const PackedByteArray &blocked, int cols, int rows, int start) {
	const int n = cols * rows;
	const double INF_ = std::numeric_limits<double>::infinity();
	PackedFloat64Array cost_arr;
	cost_arr.resize(n);
	PackedFloat32Array dirx_arr;
	dirx_arr.resize(n);
	PackedFloat32Array diry_arr;
	diry_arr.resize(n);
	double *cost = cost_arr.ptrw();
	float *dirX = dirx_arr.ptrw();
	float *dirY = diry_arr.ptrw();
	for (int i = 0; i < n; i++) {
		cost[i] = INF_;
		dirX[i] = 0.0f;
		dirY[i] = 0.0f;
	}
	Array out;
	if (start < 0 || start >= n) {
		out.append(cost_arr);
		out.append(dirx_arr);
		out.append(diry_arr);
		return out;
	}
	const uint8_t *blk = blocked.ptr();
	const int W = cols + 2;
	std::vector<uint8_t> pb((size_t)W * (rows + 2), 1);
	for (int y = 0; y < rows; y++) {
		for (int x = 0; x < cols; x++) {
			pb[(size_t)(y + 1) * W + 1 + x] = blk[y * cols + x] == 1 ? 1 : 0;
		}
	}
	std::vector<uint8_t> closed((size_t)n, 0);
	cost[start] = 0.0;
	std::vector<double> hc;
	std::vector<int32_t> hi;
	hc.reserve(1024);
	hi.reserve(1024);
	hc.push_back(0.0);
	hi.push_back(start);
	size_t hn = 1;
	while (hn > 0) {
		double c = hc[0];
		int32_t i = hi[0];
		size_t last = hn - 1;
		double lastC = hc[last];
		int32_t lastI = hi[last];
		hn = last;
		if (last > 0) {
			hc[0] = lastC;
			hi[0] = lastI;
			size_t p = 0;
			while (true) {
				size_t l = 2 * p + 1;
				size_t r = 2 * p + 2;
				size_t s = p;
				if (l < last && hc[l] < hc[s]) s = l;
				if (r < last && hc[r] < hc[s]) s = r;
				if (s == p) break;
				double tc = hc[p];
				int32_t ti = hi[p];
				hc[p] = hc[s];
				hi[p] = hi[s];
				hc[s] = tc;
				hi[s] = ti;
				p = s;
			}
		}
		if (closed[i] != 0) continue;
		closed[i] = 1;
		if (c > cost[i]) continue;
		int cx = i % cols;
		int cy = i / cols;
		int pi = (cy + 1) * W + cx + 1;
		for (int k = 0; k < 8; k++) {
			int dx = NDX[k];
			int dy = NDY[k];
			if (pb[pi + dy * W + dx] == 1) continue;
			if (k >= 4 && pb[pi + dx] == 1 && pb[pi + dy * W] == 1) continue;
			int ni = i + dy * cols + dx;
			if (closed[ni] != 0) continue;
			double nc = c + NW[k];
			if (nc < cost[ni]) {
				cost[ni] = nc;
				if (hn < hc.size()) {
					hc[hn] = nc;
					hi[hn] = ni;
				} else {
					hc.push_back(nc);
					hi.push_back(ni);
				}
				size_t ci = hn;
				hn++;
				while (ci > 0) {
					size_t pp = (ci - 1) >> 1;
					if (hc[pp] <= hc[ci]) break;
					double tc2 = hc[pp];
					int32_t ti2 = hi[pp];
					hc[pp] = hc[ci];
					hi[pp] = hi[ci];
					hc[ci] = tc2;
					hi[ci] = ti2;
					ci = pp;
				}
			}
		}
	}
	// Directions: toward the lowest-cost neighbour (diagonals never through a
	// blocked corner). bx / hypot(bx, by) for |bx|,|by| <= 1 is ±1 or ±1/√2.
	const double inv_sqrt2 = 1.0 / std::sqrt(2.0);
	for (int cy = 0; cy < rows; cy++) {
		for (int cx = 0; cx < cols; cx++) {
			int i = cy * cols + cx;
			if (cost[i] == INF_) continue;
			double best = cost[i];
			int bx = 0, by = 0;
			int pi = (cy + 1) * W + cx + 1;
			for (int k = 0; k < 8; k++) {
				int dx = NDX[k];
				int dy = NDY[k];
				int nx = cx + dx;
				int ny = cy + dy;
				if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
				if (k >= 4 && (pb[pi + dx] == 1 || pb[pi + dy * W] == 1)) continue;
				double nc = cost[i + dy * cols + dx];
				if (nc < best) {
					best = nc;
					bx = dx;
					by = dy;
				}
			}
			if (bx == 0 && by == 0) {
				dirX[i] = 0.0f;
				dirY[i] = 0.0f;
			} else if (bx == 0 || by == 0) {
				dirX[i] = (float)bx;
				dirY[i] = (float)by;
			} else {
				dirX[i] = (float)(bx * inv_sqrt2);
				dirY[i] = (float)(by * inv_sqrt2);
			}
		}
	}
	out.append(cost_arr);
	out.append(dirx_arr);
	out.append(diry_arr);
	return out;
}

// ---------------------------------------------------------------- A* --
// Scratch buffers reused across searches, stamped with a generation counter,
// as in astar.gd.
static std::vector<float> g_score;
static std::vector<uint32_t> g_stamp;
static std::vector<int32_t> came_from;
static std::vector<uint32_t> closed_stamp;
static std::vector<double> heap_f;
static std::vector<int32_t> heap_i;
static uint32_t generation_ = 0;

static inline void heap_push(size_t &len, int32_t i, double f) {
	if (len >= heap_f.size()) {
		size_t cap = heap_f.size() * 2;
		if (cap < 1024) cap = 1024;
		heap_f.resize(cap);
		heap_i.resize(cap);
	}
	size_t c = len++;
	heap_f[c] = f;
	heap_i[c] = i;
	while (c > 0) {
		size_t p = (c - 1) >> 1;
		if (heap_f[p] <= heap_f[c]) break;
		double tf = heap_f[p];
		int32_t ti = heap_i[p];
		heap_f[p] = heap_f[c];
		heap_i[p] = heap_i[c];
		heap_f[c] = tf;
		heap_i[c] = ti;
		c = p;
	}
}

static inline int32_t heap_pop(size_t &len) {
	int32_t top = heap_i[0];
	len--;
	double lastF = heap_f[len];
	int32_t lastI = heap_i[len];
	if (len > 0) {
		heap_f[0] = lastF;
		heap_i[0] = lastI;
		size_t p = 0;
		while (true) {
			size_t l = 2 * p + 1;
			size_t r = 2 * p + 2;
			size_t s = p;
			if (l < len && heap_f[l] < heap_f[s]) s = l;
			if (r < len && heap_f[r] < heap_f[s]) s = r;
			if (s == p) break;
			double tf = heap_f[p];
			int32_t ti = heap_i[p];
			heap_f[p] = heap_f[s];
			heap_i[p] = heap_i[s];
			heap_f[s] = tf;
			heap_i[s] = ti;
			p = s;
		}
	}
	return top;
}

PackedInt32Array BBNative::astar_search(const PackedByteArray &blocked, int cols, int rows,
		int scx, int scy, int tcx, int tcy, int max_nodes) {
	PackedInt32Array result;
	const size_t n = (size_t)cols * rows;
	if (g_score.size() < n) {
		g_score.assign(n, 0.0f);
		g_stamp.assign(n, 0);
		came_from.assign(n, 0);
		closed_stamp.assign(n, 0);
		generation_ = 0;
	}
	if (generation_ >= 0xfffffffeu) {
		std::fill(g_stamp.begin(), g_stamp.end(), 0);
		std::fill(closed_stamp.begin(), closed_stamp.end(), 0);
		generation_ = 0;
	}
	generation_++;
	const uint32_t gen = generation_;
	size_t heap_len = 0;
	const uint8_t *blk = blocked.ptr();

	int32_t start = scy * cols + scx;
	g_score[start] = 0.0f;
	g_stamp[start] = gen;
	came_from[start] = -1;
	{
		int dx = std::abs(scx - tcx);
		int dy = std::abs(scy - tcy);
		heap_push(heap_len, start, (double)(dx + dy) + (1.4142 - 2.0) * (double)std::min(dx, dy));
	}
	int32_t goal = tcy * cols + tcx;
	int expanded = 0;
	while (heap_len > 0) {
		int32_t cur = heap_pop(heap_len);
		if (cur == goal) {
			std::vector<int32_t> cells;
			int32_t c = cur;
			while (c != -1) {
				cells.push_back(c);
				c = came_from[c];
			}
			result.resize((int64_t)cells.size());
			int32_t *w = result.ptrw();
			for (size_t k = 0; k < cells.size(); k++) w[k] = cells[cells.size() - 1 - k];
			return result;
		}
		if (closed_stamp[cur] == gen) continue;
		closed_stamp[cur] = gen;
		expanded++;
		if (expanded > max_nodes) break;
		int ccx = cur % cols;
		int ccy = cur / cols;
		double gCur = (double)g_score[cur];
		for (int k = 0; k < 8; k++) {
			int nx = ccx + NDX[k];
			int ny = ccy + NDY[k];
			if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
			int ni = ny * cols + nx;
			if (blk[ni] == 1) continue;
			if (k >= 4) {
				bool sideX = nx < 0 || nx >= cols || ccy < 0 || ccy >= rows || blk[ccy * cols + nx] == 1;
				bool sideY = ccx < 0 || ccx >= cols || ny < 0 || ny >= rows || blk[ny * cols + ccx] == 1;
				if (sideX && sideY) continue;
			}
			if (closed_stamp[ni] == gen) continue;
			double ng = gCur + NW[k];
			if (g_stamp[ni] != gen || ng < (double)g_score[ni]) {
				g_score[ni] = (float)ng;
				g_stamp[ni] = gen;
				came_from[ni] = cur;
				int dx = std::abs(nx - tcx);
				int dy = std::abs(ny - tcy);
				double hn = (double)(dx + dy) + (1.4142 - 2.0) * (double)std::min(dx, dy);
				heap_push(heap_len, ni, ng + hn);
			}
		}
	}
	return result;
}

// ------------------------------------------------- path smoothing --
// V8's Math.hypot (scaled Kahan sum), as JS.hypot in GDScript.
static double js_hypot(double a, double b) {
	double x = std::fabs(a), y = std::fabs(b);
	if (std::isnan(a) || std::isnan(b)) {
		if (std::isinf(a) || std::isinf(b)) return std::numeric_limits<double>::infinity();
		return std::numeric_limits<double>::quiet_NaN();
	}
	double m = x > y ? x : y;
	if (std::isinf(m)) return std::numeric_limits<double>::infinity();
	if (m == 0.0) return 0.0;
	double s = 0.0, comp = 0.0;
	double n = x / m;
	double summand = n * n - comp;
	double prelim = s + summand;
	comp = (prelim - s) - summand;
	s = prelim;
	n = y / m;
	summand = n * n - comp;
	prelim = s + summand;
	comp = (prelim - s) - summand;
	s = prelim;
	return std::sqrt(s) * m;
}

struct GridView {
	const uint8_t *blk;
	int cols, rows;
	double tile;
	bool blocked_world(double wx, double wy) const {
		int64_t cx = (int64_t)std::floor(wx / tile);
		int64_t cy = (int64_t)std::floor(wy / tile);
		if (cx < 0 || cy < 0 || cx >= cols || cy >= rows) return true;
		return blk[cy * cols + cx] == 1;
	}
};

// astar.gd lineClear (clearance 11).
static bool line_clear(const GridView &g, double x0, double y0, double x1, double y1) {
	const double clearance = 11.0;
	double dx = x1 - x0;
	double dy = y1 - y0;
	double len = js_hypot(dx, dy);
	if (len == 0.0) len = 1.0;
	int64_t steps = (int64_t)std::ceil(len / 12.0);
	double px = (-dy / len) * clearance;
	double py = (dx / len) * clearance;
	for (int64_t i = 0; i <= steps; i++) {
		double t = (double)i / (double)steps;
		double cx = x0 + dx * t;
		double cy = y0 + dy * t;
		if (g.blocked_world(cx, cy)) return false;
		if (g.blocked_world(cx + px, cy + py)) return false;
		if (g.blocked_world(cx - px, cy - py)) return false;
	}
	return true;
}

PackedFloat64Array BBNative::astar_find(const PackedByteArray &blocked, int cols, int rows, double tile,
		int scx, int scy, int tcx, int tcy, int max_nodes, double tx, double ty) {
	PackedFloat64Array out;
	PackedInt32Array cells = astar_search(blocked, cols, rows, scx, scy, tcx, tcy, max_nodes);
	const int64_t nc = cells.size();
	if (nc == 0) return out;
	std::vector<double> pts;
	pts.reserve((size_t)nc * 2);
	const int32_t *cp = cells.ptr();
	for (int64_t k = 0; k < nc; k++) {
		int cx = cp[k] % cols;
		int cy = cp[k] / cols;
		pts.push_back((double)cx * tile + tile / 2.0);
		pts.push_back((double)cy * tile + tile / 2.0);
	}
	pts[pts.size() - 2] = tx;
	pts[pts.size() - 1] = ty;
	GridView g{ blocked.ptr(), cols, rows, tile };
	std::vector<double> res;
	if (pts.size() <= 4) {
		res = pts;
	} else {
		res.push_back(pts[0]);
		res.push_back(pts[1]);
		double ax = pts[0], ay = pts[1];
		size_t i = 2;
		while (i < pts.size() - 2) {
			double nx = pts[i + 2], ny = pts[i + 3];
			if (!line_clear(g, ax, ay, nx, ny)) {
				res.push_back(pts[i]);
				res.push_back(pts[i + 1]);
				ax = pts[i];
				ay = pts[i + 1];
			}
			i += 2;
		}
		res.push_back(pts[pts.size() - 2]);
		res.push_back(pts[pts.size() - 1]);
	}
	out.resize((int64_t)res.size());
	double *w = out.ptrw();
	for (size_t k = 0; k < res.size(); k++) w[k] = res[k];
	return out;
}
