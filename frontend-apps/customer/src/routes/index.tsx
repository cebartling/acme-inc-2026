import { createFileRoute } from "@tanstack/react-router";
import {
  Zap,
  Server,
  Route as RouteIcon,
  Shield,
  Waves,
  Sparkles,
} from "lucide-react";

export const Route = createFileRoute("/")({ component: App });

function App() {
  const features = [
    {
      icon: <Zap className="w-12 h-12 text-cyan-400" />,
      title: "Fast Delivery",
      description:
        "Lightning-fast shipping to get your orders to you quickly. Track your packages in real-time.",
    },
    {
      icon: <Server className="w-12 h-12 text-cyan-400" />,
      title: "Wide Selection",
      description:
        "Browse thousands of products across multiple categories. Find exactly what you need.",
    },
    {
      icon: <RouteIcon className="w-12 h-12 text-cyan-400" />,
      title: "Easy Returns",
      description:
        "Hassle-free returns within 30 days. Your satisfaction is our priority.",
    },
    {
      icon: <Shield className="w-12 h-12 text-cyan-400" />,
      title: "Secure Shopping",
      description:
        "Shop with confidence. Your data is protected with industry-leading security.",
    },
    {
      icon: <Waves className="w-12 h-12 text-cyan-400" />,
      title: "24/7 Support",
      description:
        "Our customer service team is always here to help. Get support whenever you need it.",
    },
    {
      icon: <Sparkles className="w-12 h-12 text-cyan-400" />,
      title: "Exclusive Deals",
      description:
        "Members get access to special discounts and early access to new products.",
    },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900">
      <section className="relative py-20 px-6 text-center overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-r from-cyan-500/10 via-blue-500/10 to-purple-500/10"></div>
        <div className="relative max-w-5xl mx-auto">
          <div className="flex items-center justify-center gap-6 mb-6">
            <img
              src="/acme-logo.svg"
              alt="ACME, Inc. Logo"
              className="w-24 h-24 md:w-32 md:h-32"
            />
            <h1 className="text-6xl md:text-7xl font-black text-white [letter-spacing:-0.08em]">
              <span className="text-gray-300">ACME</span>{" "}
              <span className="bg-gradient-to-r from-cyan-400 to-blue-400 bg-clip-text text-transparent">
                Inc.
              </span>
            </h1>
          </div>
          <p className="text-2xl md:text-3xl text-gray-300 mb-4 font-light">
            Your trusted e-commerce partner
          </p>
          <p className="text-lg text-gray-400 max-w-3xl mx-auto mb-8">
            Discover quality products and exceptional service. ACME, Inc.
            delivers innovation and reliability for all your shopping needs.
          </p>
          <div className="flex flex-col items-center gap-4">
            <a
              href="/products"
              className="px-8 py-3 bg-cyan-500 hover:bg-cyan-600 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-cyan-500/50"
            >
              Shop Now
            </a>
            <p className="text-gray-400 text-sm mt-2">
              Welcome to ACME, Inc. &mdash; Quality you can trust
            </p>
          </div>
        </div>
      </section>

      <section className="py-16 px-6 max-w-7xl mx-auto">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map((feature, index) => (
            <div
              key={index}
              className="bg-slate-800/50 backdrop-blur-sm border border-slate-700 rounded-xl p-6 hover:border-cyan-500/50 transition-all duration-300 hover:shadow-lg hover:shadow-cyan-500/10"
            >
              <div className="mb-4">{feature.icon}</div>
              <h3 className="text-xl font-semibold text-white mb-3">
                {feature.title}
              </h3>
              <p className="text-gray-400 leading-relaxed">
                {feature.description}
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
