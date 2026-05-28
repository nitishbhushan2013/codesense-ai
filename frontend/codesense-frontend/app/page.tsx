import SubmitForm from "@/components/SubmitForm";

export default function Home() {
  return (
    <div className="min-h-screen px-4 py-12 md:py-20">
      <div className="max-w-6xl mx-auto">
        {/* Hero */}
        <div className="text-center mb-12 md:mb-16">
          <h1 className="text-4xl md:text-6xl font-bold text-white mb-4 md:mb-6">
            AI code review
            <br />
            in seconds
          </h1>
          <p className="text-gray-400 text-lg md:text-xl max-w-2xl mx-auto">
            Paste a GitHub PR link or raw code and get a structured review of
            bugs, security issues, performance, and quality — each with a
            suggested fix.
          </p>
        </div>

        {/* Submit form */}
        <SubmitForm />

        <p className="text-center text-gray-500 text-sm mt-6">
          No sign-up required to try.
        </p>
      </div>
    </div>
  );
}
